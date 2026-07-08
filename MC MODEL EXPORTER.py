"""
mc_model_exporter.py
Blender Addon — Minecraft / TimelessLib Model + Animation Exporter

Exports full scene data (geometry, materials, textures, skeletons, skinning
and animation) to a compact binary ".tlmdl" container instead of JSON.

This is the successor to the JSON-only "MC Anim Exporter". It keeps that
add-on's interpolation / easing mapping (so it stays compatible with
TimelessLib's animation runtime) but adds everything you'd expect from an
FBX-style pipeline:

  * Scene graph (node hierarchy + local TRS transforms)
  * Meshes      (positions, normals, tangents, multiple UV + colour layers,
                 triangulated, per-triangle material slot)
  * Materials   (Principled BSDF: base colour, metallic, roughness, emissive,
                 alpha, double-sided) with texture bindings
  * Textures    (external path OR embedded PNG bytes, wrap + filter modes)
  * Skeletons   (armature bones, rest TRS, inverse-bind matrices)
  * Skinning    (per-vertex bone influences, configurable max influences)
  * Animations  (object + pose-bone F-curves, TimelessLib interp/easing)

The output is a chunked, little-endian binary file. Large blocks are
zlib-compressed. See TLMDL_FORMAT.md for the full byte-level specification.

Install:  Edit > Preferences > Add-ons > Install > select this file > enable
Usage:    N-panel > "MC Model" tab, pick a source mode, Export
"""

bl_info = {
    "name":        "MC Model Exporter (TLMDL)",
    "author":      "TimelessLib Pipeline",
    "version":     (2, 3, 0),
    "blender":     (4, 0, 0),
    "location":    "View3D > Sidebar > MC Model",
    "description": "Export models + animations to the TimelessLib binary .tlmdl format",
    "category":    "Import-Export",
}

import bpy
import os
import struct
import zlib
from mathutils import Matrix
from bpy.props import BoolProperty, StringProperty, EnumProperty, IntProperty
from bpy.types import Panel, Operator, PropertyGroup


# ═════════════════════════════════════════════════════════════════════════
#  Format constants
# ═════════════════════════════════════════════════════════════════════════

MAGIC          = b"TLMDL"
VERSION_MAJOR  = 2
VERSION_MINOR  = 1

# Chunk identifiers (4 ASCII bytes each)
CHUNK_STRT = b"STRT"   # string table  (must come first, others reference it)
CHUNK_META = b"META"   # scene metadata
CHUNK_NODE = b"NODE"   # scene graph
CHUNK_MESH = b"MESH"   # geometry
CHUNK_MATL = b"MATL"   # materials
CHUNK_TEXR = b"TEXR"   # textures / images
CHUNK_SKEL = b"SKEL"   # skeletons / armatures
CHUNK_ANIM = b"ANIM"   # animation clips
CHUNK_END  = b"END "   # terminator

COMPRESS_THRESHOLD = 256          # only bother zlib-ing payloads bigger than this
NULL_INDEX         = 0xFFFFFFFF   # "no string" / "no reference" sentinel

# Node types
NODE_EMPTY, NODE_MESH, NODE_ARMATURE, NODE_LIGHT, NODE_CAMERA = 0, 1, 2, 3, 4

# Animation channel types (u8). Mirrors Blender transform channels.
CH_LOC_X, CH_LOC_Y, CH_LOC_Z             = 0, 1, 2
CH_QUAT_W, CH_QUAT_X, CH_QUAT_Y, CH_QUAT_Z = 3, 4, 5, 6
CH_EUL_X, CH_EUL_Y, CH_EUL_Z             = 7, 8, 9
CH_SCALE_X, CH_SCALE_Y, CH_SCALE_Z       = 10, 11, 12
CH_CUSTOM                                 = 255

# Animation target types
TARGET_NODE, TARGET_BONE = 0, 1

# Interpolation ordinals. 0..3 match TimelessLib's Interpolation.java exactly.
# BEZIER (4) is a format-level extension carrying Blender's raw graph-editor
# handles so custom curves round-trip losslessly (the importer evaluates it as
# a cubic Bezier F-curve segment).
INTERP_STEP, INTERP_LINEAR, INTERP_EASE, INTERP_CATMULL = 0, 1, 2, 3
INTERP_BEZIER = 4

# Texture usage slots (u8)
TEX_BASE_COLOR, TEX_NORMAL, TEX_METALLIC_ROUGHNESS = 0, 1, 2
TEX_EMISSIVE, TEX_OCCLUSION, TEX_ROUGHNESS, TEX_METALLIC = 3, 4, 5, 6


# Maps a raw sub-path (last transform token) + array index → channel type id.
# Used for both object channels and pose-bone channels.
TRANSFORM_CHANNELS = {
    ("location", 0): CH_LOC_X, ("location", 1): CH_LOC_Y, ("location", 2): CH_LOC_Z,
    ("rotation_quaternion", 0): CH_QUAT_W, ("rotation_quaternion", 1): CH_QUAT_X,
    ("rotation_quaternion", 2): CH_QUAT_Y, ("rotation_quaternion", 3): CH_QUAT_Z,
    ("rotation_euler", 0): CH_EUL_X, ("rotation_euler", 1): CH_EUL_Y, ("rotation_euler", 2): CH_EUL_Z,
    ("scale", 0): CH_SCALE_X, ("scale", 1): CH_SCALE_Y, ("scale", 2): CH_SCALE_Z,
}

# Blender's Robert-Penner "equation" interpolation modes are literally the
# easings.net set — the same functions TimelessLib's Easing.java implements — so
# they map to a named easing with zero loss. CONSTANT/LINEAR map to STEP/LINEAR.
# BEZIER is NOT in this table: it is handled separately by storing raw handles,
# so any curve you shape in the graph editor is preserved exactly.
#
# Maps blender_interpolation → { blender_easing → Easing.java field name }.
# The base name has an EASE_IN_ / EASE_OUT_ / EASE_IN_OUT_ prefix applied.
_EQUATION_BASES = ["SINE", "QUAD", "CUBIC", "QUART", "QUINT",
                   "EXPO", "CIRC", "BACK", "BOUNCE", "ELASTIC"]

EQUATION_EASING = {}
for _base in _EQUATION_BASES:
    EQUATION_EASING[(_base, "EASE_IN")]     = f"EASE_IN_{_base}"
    EQUATION_EASING[(_base, "EASE_OUT")]    = f"EASE_OUT_{_base}"
    EQUATION_EASING[(_base, "EASE_IN_OUT")] = f"EASE_IN_OUT_{_base}"
    # Blender's "Automatic Easing" default eases both ends for these curves.
    EQUATION_EASING[(_base, "AUTO")]        = f"EASE_IN_OUT_{_base}"


def classify_keyframe(kp):
    """
    Classify a Blender keyframe as close to the graph editor as possible.

    Returns (interp_ordinal, easing_name_or_None, is_bezier).
      * CONSTANT           → (STEP,   None,        False)
      * LINEAR             → (LINEAR, None,        False)
      * BEZIER             → (BEZIER, None,        True)   handles stored separately
      * SINE/QUAD/…/ELASTIC→ (EASE,   "<easing>",  False)  exact easings.net match
    """
    interp = kp.interpolation
    easing = kp.easing

    if interp == "CONSTANT":
        return INTERP_STEP, None, False
    if interp == "LINEAR":
        return INTERP_LINEAR, None, False
    if interp == "BEZIER":
        return INTERP_BEZIER, None, True

    name = EQUATION_EASING.get((interp, easing))
    if name is not None:
        return INTERP_EASE, name, False
    # Unknown easing variant of a known curve → default to symmetric ease.
    if interp in _EQUATION_BASES:
        return INTERP_EASE, f"EASE_IN_OUT_{interp}", False
    # Total fallback.
    return INTERP_EASE, "EASE_IN_OUT_SINE", False


# ═════════════════════════════════════════════════════════════════════════
#  Binary writer
# ═════════════════════════════════════════════════════════════════════════

class StringTable:
    """Deduplicating pool of UTF-8 strings referenced by u32 index elsewhere."""

    def __init__(self):
        self._list = []
        self._index = {}

    def add(self, s):
        """Return the index for `s`, or NULL_INDEX for None."""
        if s is None:
            return NULL_INDEX
        s = str(s)
        idx = self._index.get(s)
        if idx is None:
            idx = len(self._list)
            self._list.append(s)
            self._index[s] = idx
        return idx

    def serialize(self):
        w = BinWriter()
        w.u32(len(self._list))
        for s in self._list:
            data = s.encode("utf-8")
            w.u32(len(data))
            w.raw(data)
        return bytes(w)


class BinWriter:
    """Little-endian primitive writer backed by a bytearray."""

    def __init__(self):
        self.buf = bytearray()

    def raw(self, b):        self.buf += b
    def u8(self, v):         self.buf += struct.pack("<B", v & 0xFF)
    def i8(self, v):         self.buf += struct.pack("<b", v)
    def u16(self, v):        self.buf += struct.pack("<H", v & 0xFFFF)
    def u32(self, v):        self.buf += struct.pack("<I", v & 0xFFFFFFFF)
    def i32(self, v):        self.buf += struct.pack("<i", v)
    def f32(self, v):        self.buf += struct.pack("<f", v)
    def f64(self, v):        self.buf += struct.pack("<d", v)

    def vec2(self, v):       self.buf += struct.pack("<2f", v[0], v[1])
    def vec3(self, v):       self.buf += struct.pack("<3f", v[0], v[1], v[2])
    def vec4(self, v):       self.buf += struct.pack("<4f", v[0], v[1], v[2], v[3])

    def quat(self, q):
        # Blender quaternion order is (w, x, y, z); stored the same way.
        self.buf += struct.pack("<4f", q.w, q.x, q.y, q.z)

    def matrix4(self, m):
        # Row-major: m[row][col], 16 floats.
        for r in range(4):
            for c in range(4):
                self.buf += struct.pack("<f", m[r][c])

    def color_u8(self, c):
        self.buf += struct.pack("<4B",
                                clamp_u8(c[0]), clamp_u8(c[1]),
                                clamp_u8(c[2]), clamp_u8(c[3] if len(c) > 3 else 1.0))

    def __bytes__(self):
        return bytes(self.buf)

    def __len__(self):
        return len(self.buf)


def clamp_u8(f):
    return max(0, min(255, int(round(f * 255.0))))


def write_chunk(out: bytearray, chunk_id: bytes, payload: bytes):
    """Append a length-prefixed (optionally zlib-compressed) chunk to `out`."""
    raw_size = len(payload)
    stored = payload
    compression = 0
    if raw_size > COMPRESS_THRESHOLD:
        packed = zlib.compress(payload, 9)
        if len(packed) < raw_size:
            stored = packed
            compression = 1
    out += chunk_id
    out += struct.pack("<B", compression)
    out += struct.pack("<I", len(stored))
    out += struct.pack("<I", raw_size)
    out += stored


# ═════════════════════════════════════════════════════════════════════════
#  Coordinate conversion
# ═════════════════════════════════════════════════════════════════════════

# Blender is Z-up / right-handed. Minecraft & most engines are Y-up.
# Rotate -90° about X so +Z(Blender) → +Y(target). Applied only to ROOT node
# local transforms, so mesh vertex data (in object-local space) is untouched
# and the whole hierarchy reorients cleanly.
YUP_CONVERSION = Matrix.Rotation(-1.5707963267948966, 4, 'X')


# ═════════════════════════════════════════════════════════════════════════
#  Extraction context
# ═════════════════════════════════════════════════════════════════════════

class ExportContext:
    """Holds the shared string table + dedup registries during a single export."""

    def __init__(self, settings, fps):
        self.strings   = StringTable()
        self.settings  = settings
        self.fps       = fps
        self.scene_name = ""       # filled by export_scene
        self.frame_end  = 0        # scene frame_end, filled by export_scene

        # object registries
        self.objects        = []          # ordered exported bpy objects
        self.obj_to_node    = {}          # bpy object → node index

        # datablock dedup: name → global index
        self.materials      = []          # list of bpy materials
        self.material_index = {}          # bpy material → index
        self.images         = []          # list of bpy images
        self.image_index    = {}          # bpy image → index

        # armature registries
        self.armatures      = []          # list of bpy armature objects
        self.arm_to_skel    = {}          # armature object → skeleton index
        # skeleton index → {bone_name: bone_index}
        self.bone_maps      = []

    # -- string helper ------------------------------------------------------
    def s(self, text):
        return self.strings.add(text)

    # -- material dedup -----------------------------------------------------
    def material_ref(self, mat):
        if mat is None:
            return -1
        idx = self.material_index.get(mat.name)
        if idx is None:
            idx = len(self.materials)
            self.materials.append(mat)
            self.material_index[mat.name] = idx
        return idx

    # -- image dedup --------------------------------------------------------
    def image_ref(self, image):
        if image is None:
            return -1
        idx = self.image_index.get(image.name)
        if idx is None:
            idx = len(self.images)
            self.images.append(image)
            self.image_index[image.name] = idx
        return idx


# ═════════════════════════════════════════════════════════════════════════
#  Object collection
# ═════════════════════════════════════════════════════════════════════════

def node_type_of(obj):
    if obj.type == 'MESH':      return NODE_MESH
    if obj.type == 'ARMATURE':  return NODE_ARMATURE
    if obj.type == 'LIGHT':     return NODE_LIGHT
    if obj.type == 'CAMERA':    return NODE_CAMERA
    return NODE_EMPTY


def collect_objects(context, settings):
    """Return the ordered list of objects to export based on the source mode."""
    scene = context.scene
    mode = settings.export_mode

    if mode == 'SELECTED':
        pool = list(context.selected_objects)
    elif mode == 'TAGGED':
        pool = [o for o in scene.objects if o.get("mc_export", False)]
    else:  # ALL
        pool = list(scene.objects)

    pool_set = set(pool)

    # Pull in parents so the hierarchy stays connected, then sort parents-first.
    expanded = set(pool)
    for obj in pool:
        p = obj.parent
        while p is not None:
            expanded.add(p)
            p = p.parent

    # Pull in armatures referenced by the exported meshes' Armature modifiers,
    # otherwise their skin weights (and bone animation) would be silently
    # dropped when the armature object itself wasn't selected/tagged.
    for obj in list(expanded):
        for m in obj.modifiers:
            if m.type == 'ARMATURE' and m.object is not None:
                arm = m.object
                expanded.add(arm)
                p = arm.parent
                while p is not None:
                    expanded.add(p)
                    p = p.parent

    def depth(o):
        d, p = 0, o.parent
        while p is not None:
            d += 1
            p = p.parent
        return d

    ordered = sorted(expanded, key=lambda o: (depth(o), o.name))
    return ordered, pool_set


# ═════════════════════════════════════════════════════════════════════════
#  NODE chunk
# ═════════════════════════════════════════════════════════════════════════

def build_nodes(ctx):
    """Serialize the scene graph. Populates ctx.obj_to_node and armature list."""
    objects = ctx.objects
    for i, obj in enumerate(objects):
        ctx.obj_to_node[obj] = i
        if obj.type == 'ARMATURE' and obj not in ctx.arm_to_skel:
            ctx.arm_to_skel[obj] = len(ctx.armatures)
            ctx.armatures.append(obj)

    w = BinWriter()
    w.u32(len(objects))
    for obj in objects:
        parent_idx = ctx.obj_to_node.get(obj.parent, -1)
        ntype = node_type_of(obj)

        # Store the NATIVE local transform (matrix_basis) — this is exactly what
        # the animation channels (location / rotation_* / scale) drive, so rest
        # and animated poses live in the same space and stay consistent.
        #
        # Blender reconstructs world as:
        #     matrix_world = parent.matrix_world @ matrix_parent_inverse @ matrix_basis
        # so we also export matrix_parent_inverse and let the importer apply the
        # same formula. The Z-up→Y-up conversion is NOT baked here; it is a single
        # global root_transform in META (see build_meta), which keeps animated
        # roots from losing the conversion.
        t, r, s = obj.matrix_basis.decompose()

        w.u32(ctx.s(obj.name))
        w.i32(parent_idx)
        w.u8(ntype)
        w.vec3(t)
        w.quat(r)
        w.vec3(s)
        w.matrix4(obj.matrix_parent_inverse)   # constant parent-inverse offset
        w.i32(ctx.arm_to_skel.get(obj, -1) if obj.type == 'ARMATURE' else -1)

    return bytes(w)


# The mesh_index per node is resolved in a second pass once meshes exist.
# To keep the format simple we instead store the mapping inside the MESH chunk
# (each mesh records the node index it belongs to). See build_meshes.


# ═════════════════════════════════════════════════════════════════════════
#  MESH chunk
# ═════════════════════════════════════════════════════════════════════════

def get_corner_normals(mesh):
    """Return a function loop_index → normal, across Blender versions."""
    # Blender 4.1+: mesh.corner_normals; else compute split normals.
    if hasattr(mesh, "corner_normals") and len(mesh.corner_normals) == len(mesh.loops):
        data = mesh.corner_normals
        return lambda li: data[li].vector
    try:
        mesh.calc_normals_split()
    except Exception:
        pass
    loops = mesh.loops
    return lambda li: loops[li].normal


def gather_skin(obj, mesh, ctx, max_influences):
    """
    Return (skel_index, per_vertex_influences) or (-1, None).
    per_vertex_influences[v] = list of (bone_index, weight) length==max_influences.
    """
    arm_obj = None
    for mod in obj.modifiers:
        if mod.type == 'ARMATURE' and mod.object is not None:
            arm_obj = mod.object
            break
    if arm_obj is None or arm_obj not in ctx.arm_to_skel:
        return -1, None

    skel_index = ctx.arm_to_skel[arm_obj]
    bone_map = ctx.bone_maps[skel_index] if skel_index < len(ctx.bone_maps) else {}

    # group index → bone index
    grp_to_bone = {}
    for gi, vg in enumerate(obj.vertex_groups):
        b = bone_map.get(vg.name)
        if b is not None:
            grp_to_bone[gi] = b

    influences = []
    for v in mesh.vertices:
        weights = []
        for g in v.groups:
            b = grp_to_bone.get(g.group)
            if b is not None and g.weight > 0.0:
                weights.append((b, g.weight))
        weights.sort(key=lambda x: x[1], reverse=True)
        weights = weights[:max_influences]
        total = sum(w for _, w in weights)
        if total > 0.0:
            weights = [(b, w / total) for b, w in weights]
        while len(weights) < max_influences:
            weights.append((0, 0.0))
        influences.append(weights)
    return skel_index, influences


def build_meshes(ctx):
    """Serialize all mesh objects into the MESH chunk. Returns (bytes, node_mesh_map)."""
    settings = ctx.settings
    depsgraph = bpy.context.evaluated_depsgraph_get() if settings.apply_modifiers else None
    max_influences = settings.max_influences

    meshes_bin = BinWriter()
    mesh_records = []          # (node_index, payload_writer)
    node_mesh_map = {}         # node_index → mesh_index

    mesh_objects = [o for o in ctx.objects if o.type == 'MESH']

    for obj in mesh_objects:
        # A skinned mesh must be exported in its REST pose with intact vertex
        # groups. Evaluating the armature modifier would bake the deform into
        # the positions and invalidate the weights, so for those meshes we skip
        # modifier evaluation and read the original (rest) mesh instead.
        has_armature_mod = any(
            m.type == 'ARMATURE' and m.object is not None for m in obj.modifiers
        )
        use_eval = settings.apply_modifiers and not has_armature_mod

        if use_eval:
            eval_obj = obj.evaluated_get(depsgraph)
            mesh = eval_obj.to_mesh()
        else:
            mesh = obj.to_mesh()

        try:
            mesh.calc_loop_triangles()
            normal_of = get_corner_normals(mesh)

            uv_layers = list(mesh.uv_layers)
            color_layers = list(mesh.color_attributes)

            skel_index, influences = gather_skin(obj, mesh, ctx, max_influences)
            has_skin = influences is not None

            # -- Build a unified, de-duplicated vertex buffer -----------------
            unique = {}        # key → new vertex index
            positions, normals, uvs, colors, skins = [], [], [], [], []
            uvs = [[] for _ in uv_layers]
            colors = [[] for _ in color_layers]
            indices = []       # flat triangle corner → vertex index
            tri_materials = []

            def color_at(layer, loop):
                if layer.domain == 'POINT':
                    return layer.data[loop.vertex_index].color
                return layer.data[loop.index].color

            for tri in mesh.loop_triangles:
                for k in range(3):
                    loop_index = tri.loops[k]
                    loop = mesh.loops[loop_index]
                    vidx = loop.vertex_index

                    nrm = normal_of(loop_index)
                    uv_vals = tuple(
                        (round(l.data[loop_index].uv[0], 6), round(l.data[loop_index].uv[1], 6))
                        for l in uv_layers
                    )
                    col_vals = tuple(
                        tuple(round(c, 5) for c in color_at(l, loop))
                        for l in color_layers
                    )
                    key = (vidx,
                           round(nrm[0], 5), round(nrm[1], 5), round(nrm[2], 5),
                           uv_vals, col_vals)

                    new_idx = unique.get(key)
                    if new_idx is None:
                        new_idx = len(positions)
                        unique[key] = new_idx
                        positions.append(mesh.vertices[vidx].co.copy())
                        normals.append((nrm[0], nrm[1], nrm[2]))
                        for li, l in enumerate(uv_layers):
                            uvs[li].append(l.data[loop_index].uv.copy())
                        for ci, l in enumerate(color_layers):
                            uvs_c = color_at(l, loop)
                            colors[ci].append(tuple(uvs_c))
                        if has_skin:
                            skins.append(influences[vidx])
                    indices.append(new_idx)
                tri_materials.append(tri.material_index)

            # -- Resolve local material slots → global material indices -------
            slot_globals = []
            for slot in obj.material_slots:
                slot_globals.append(ctx.material_ref(slot.material))
            if not slot_globals:
                slot_globals = [-1]

            vcount = len(positions)
            index_size = 2 if vcount <= 0xFFFF else 4

            # -- Emit one mesh record ----------------------------------------
            m = BinWriter()
            m.u32(ctx.s(mesh.name if mesh.name else obj.name))
            m.u32(vcount)
            m.u8(len(uv_layers))
            m.u8(len(color_layers))
            flags = 0
            flags |= 1          # normals always present
            if has_skin:
                flags |= 4
            m.u8(flags)
            m.u8(max_influences if has_skin else 0)
            m.i32(skel_index)

            # positions
            for p in positions:
                m.vec3(p)
            # normals
            for n in normals:
                m.vec3(n)
            # uv layers
            for li in range(len(uv_layers)):
                for uv in uvs[li]:
                    m.vec2(uv)
            # color layers (u8 RGBA)
            for ci in range(len(color_layers)):
                for c in colors[ci]:
                    m.color_u8(c)
            # skin (bone u16 + weight f32) × max_influences
            if has_skin:
                for infl in skins:
                    for b, wgt in infl:
                        m.u16(b)
                        m.f32(wgt)

            # material slot table
            m.u16(len(slot_globals))
            for g in slot_globals:
                m.i32(g)

            # triangles
            tri_count = len(tri_materials)
            m.u32(tri_count)
            m.u8(index_size)
            for i, mat_slot in enumerate(tri_materials):
                a, b, c = indices[i * 3], indices[i * 3 + 1], indices[i * 3 + 2]
                if index_size == 2:
                    m.u16(a); m.u16(b); m.u16(c)
                else:
                    m.u32(a); m.u32(b); m.u32(c)
                m.u16(mat_slot)

            node_index = ctx.obj_to_node[obj]
            mesh_index = len(mesh_records)
            node_mesh_map[node_index] = mesh_index
            mesh_records.append((node_index, bytes(m)))
        finally:
            if use_eval:
                eval_obj.to_mesh_clear()
            else:
                obj.to_mesh_clear()

    meshes_bin.u32(len(mesh_records))
    for node_index, payload in mesh_records:
        meshes_bin.i32(node_index)
        meshes_bin.raw(payload)

    return bytes(meshes_bin), node_mesh_map


# ═════════════════════════════════════════════════════════════════════════
#  MATL chunk
# ═════════════════════════════════════════════════════════════════════════

def find_principled(mat):
    if not mat or not mat.use_nodes:
        return None
    for node in mat.node_tree.nodes:
        if node.type == 'BSDF_PRINCIPLED':
            return node
    return None


def _image_from_socket(socket, seen=None):
    """Walk upstream from a linked input socket to the first Image Texture node,
    transparently passing through intermediate nodes (Normal Map, Bump, Mapping,
    reroutes, color-adjust / mix nodes, …) that sit between the image and the
    shader input."""
    if seen is None:
        seen = set()
    if socket is None or not socket.is_linked:
        return None
    for link in socket.links:
        node = link.from_node
        if node is None or node.name in seen:
            continue
        seen.add(node.name)
        if node.type == 'TEX_IMAGE' and node.image:
            return node.image
        # Not an image node — recurse through this node's own inputs.
        for inp in node.inputs:
            img = _image_from_socket(inp, seen)
            if img is not None:
                return img
    return None


def texture_from_input(node, socket_name):
    """Follow a Principled input socket back to a connected Image Texture node,
    traversing any intermediate nodes along the way."""
    if node is None or socket_name not in node.inputs:
        return None
    return _image_from_socket(node.inputs[socket_name])


def first_image_in_material(mat):
    """Fallback: return the first Image Texture datablock used anywhere in the
    material's node tree. Covers unlit/emission setups (no Principled BSDF) and
    images that are only connected to a Material Output."""
    if not mat or not mat.use_nodes or not mat.node_tree:
        return None
    for node in mat.node_tree.nodes:
        if node.type == 'TEX_IMAGE' and node.image:
            return node.image
    return None


def input_value(node, socket_name, default):
    if node is None or socket_name not in node.inputs:
        return default
    try:
        return node.inputs[socket_name].default_value
    except Exception:
        return default


def build_materials(ctx):
    w = BinWriter()
    w.u32(len(ctx.materials))
    for mat in ctx.materials:
        bsdf = find_principled(mat)

        base_color = input_value(bsdf, "Base Color", (0.8, 0.8, 0.8, 1.0))
        metallic   = float(input_value(bsdf, "Metallic", 0.0))
        roughness  = float(input_value(bsdf, "Roughness", 0.5))
        emission   = input_value(bsdf, "Emission Color", (0.0, 0.0, 0.0, 1.0))
        emis_str   = float(input_value(bsdf, "Emission Strength", 0.0))
        alpha      = float(input_value(bsdf, "Alpha", 1.0))

        flags = 0
        if not mat.use_backface_culling:
            flags |= 1  # double sided
        if mat.blend_method not in ('OPAQUE',):
            flags |= 2  # transparent / blended

        w.u32(ctx.s(mat.name))
        w.vec4((base_color[0], base_color[1], base_color[2],
                base_color[3] if len(base_color) > 3 else 1.0))
        w.f32(metallic)
        w.f32(roughness)
        w.vec3((emission[0], emission[1], emission[2]))
        w.f32(emis_str)
        w.f32(alpha)
        w.u8(flags)

        # texture bindings
        bindings = []
        pairs = [
            (TEX_BASE_COLOR, "Base Color"),
            (TEX_METALLIC_ROUGHNESS, "Roughness"),
            (TEX_METALLIC, "Metallic"),
            (TEX_EMISSIVE, "Emission Color"),
            (TEX_NORMAL, "Normal"),
        ]
        for usage, socket in pairs:
            img = texture_from_input(bsdf, socket)
            if img is not None:
                bindings.append((usage, ctx.image_ref(img)))

        # Fallback: no base-color texture found via the BSDF (unlit/emission
        # material, image wired only to Material Output, or no Principled node
        # at all) — bind the first image texture used anywhere in the material.
        if not any(u == TEX_BASE_COLOR for u, _ in bindings):
            img = first_image_in_material(mat)
            if img is not None:
                bindings.append((TEX_BASE_COLOR, ctx.image_ref(img)))

        w.u8(len(bindings))
        for usage, tex_idx in bindings:
            w.u8(usage)
            w.i32(tex_idx)

    return bytes(w)


# ═════════════════════════════════════════════════════════════════════════
#  TEXR chunk
# ═════════════════════════════════════════════════════════════════════════

WRAP_MAP = {'REPEAT': 0, 'EXTEND': 1, 'CLIP': 1, 'MIRROR': 2}


def build_textures(ctx):
    embed = ctx.settings.embed_textures
    w = BinWriter()
    w.u32(len(ctx.images))
    for img in ctx.images:
        w.u32(ctx.s(img.name))

        # wrap + filter (best-effort; images don't carry these, sampler does)
        wrap = 0
        interp = 0  # 0 nearest, 1 linear — Minecraft usually nearest
        w.u8(wrap)
        w.u8(wrap)
        w.u8(interp)

        data = None
        if embed:
            data = read_image_bytes(img)

        if data is not None:
            w.u8(1)                          # source = embedded
            w.u32(ctx.s(_basename(img)))     # original filename
            w.u32(len(data))
            w.raw(data)
        else:
            w.u8(0)                          # source = external path
            path = img.filepath_from_user() if hasattr(img, "filepath_from_user") else img.filepath
            w.u32(ctx.s(bpy.path.abspath(path) if path else img.name))

    return bytes(w)


def _basename(img):
    p = img.filepath
    return os.path.basename(bpy.path.abspath(p)) if p else (img.name + ".png")


def read_image_bytes(img):
    """Return encoded image bytes (packed data, on-disk file, or a PNG bake)."""
    # 1. Packed inside the .blend
    if img.packed_file is not None:
        try:
            return bytes(img.packed_file.data)
        except Exception:
            pass
    # 2. On-disk file
    try:
        path = bpy.path.abspath(img.filepath_from_user())
        if path and os.path.isfile(path):
            with open(path, "rb") as f:
                return f.read()
    except Exception:
        pass
    # 3. Generated / render result → bake to PNG via a temp file
    try:
        import tempfile
        tmp = os.path.join(tempfile.gettempdir(), f"_tlmdl_{img.name}.png")
        img.file_format = 'PNG'
        img.save_render(tmp) if img.type in ('RENDER_RESULT',) else img.save(filepath=tmp)
        with open(tmp, "rb") as f:
            data = f.read()
        os.remove(tmp)
        return data
    except Exception:
        return None


# ═════════════════════════════════════════════════════════════════════════
#  SKEL chunk
# ═════════════════════════════════════════════════════════════════════════

def build_skeletons(ctx):
    w = BinWriter()
    w.u32(len(ctx.armatures))

    for arm_obj in ctx.armatures:
        armature = arm_obj.data
        bones = list(armature.bones)
        bone_index = {b.name: i for i, b in enumerate(bones)}
        ctx.bone_maps.append(bone_index)

        w.u32(ctx.s(arm_obj.name))
        w.u32(len(bones))
        for bone in bones:
            parent_idx = bone_index.get(bone.parent.name, -1) if bone.parent else -1

            # Rest transform relative to the parent bone (armature space chain).
            if bone.parent:
                rest_local = bone.parent.matrix_local.inverted() @ bone.matrix_local
            else:
                rest_local = bone.matrix_local
            t, r, s = rest_local.decompose()

            # Inverse-bind matrix in armature space (row-major 4x4).
            inv_bind = bone.matrix_local.inverted()

            w.u32(ctx.s(bone.name))
            w.i32(parent_idx)
            w.vec3(t)
            w.quat(r)
            w.vec3(s)
            w.matrix4(inv_bind)
            w.f32(bone.length)

    return bytes(w)


# ═════════════════════════════════════════════════════════════════════════
#  ANIM chunk
# ═════════════════════════════════════════════════════════════════════════

def iter_fcurves(action):
    """Yield F-curves across legacy and Blender 5.0+ layered actions."""
    if hasattr(action, 'layers') and action.layers:
        for layer in action.layers:
            for strip in layer.strips:
                if hasattr(strip, 'channelbags'):
                    for cb in strip.channelbags:
                        yield from cb.fcurves
    elif hasattr(action, 'fcurves'):
        yield from action.fcurves


def parse_channel(data_path, array_index):
    """
    Return (is_bone, bone_name, channel_type, custom_name).
    channel_type is a CH_* id; custom_name is set only for CH_CUSTOM.
    """
    bone_name = None
    sub_path = data_path

    if data_path.startswith('pose.bones["'):
        end = data_path.find('"]', 12)
        bone_name = data_path[12:end]
        sub_path = data_path[end + 2:]
        if sub_path.startswith('.'):
            sub_path = sub_path[1:]

    # Custom property?  ["prop"]
    stripped = sub_path
    if stripped.startswith('["') and stripped.endswith('"]'):
        return (bone_name is not None), bone_name, CH_CUSTOM, stripped[2:-2]

    ch = TRANSFORM_CHANNELS.get((sub_path, array_index))
    if ch is not None:
        return (bone_name is not None), bone_name, ch, None

    # Unknown → treat as a custom channel keyed by the raw path+index.
    safe = sub_path.replace('"', '').replace("'", '')
    return (bone_name is not None), bone_name, CH_CUSTOM, f"{safe}.{array_index}"


def collect_action(obj, ctx):
    """Group an object's active action F-curves into tracks → channels."""
    action = obj.animation_data.action if obj.animation_data else None
    if not action:
        return None, 0.0

    node_index = ctx.obj_to_node.get(obj, -1)
    skel_index = ctx.arm_to_skel.get(obj, -1)
    bone_map = ctx.bone_maps[skel_index] if 0 <= skel_index < len(ctx.bone_maps) else {}

    # track key → list of (channel_type, custom_name, keyframes)
    tracks = {}
    last_frame = 0.0

    for fc in iter_fcurves(action):
        if not fc.keyframe_points:
            continue
        is_bone, bone_name, ch_type, custom_name = parse_channel(fc.data_path, fc.array_index)

        if is_bone:
            bidx = bone_map.get(bone_name, -1)
            if bidx < 0 or skel_index < 0:
                continue
            track_key = (TARGET_BONE, skel_index, bidx)
        else:
            if node_index < 0:
                continue
            track_key = (TARGET_NODE, node_index, -1)

        keyframes = []
        for kp in fc.keyframe_points:
            frame = kp.co[0]
            value = kp.co[1]
            tl_interp, tl_easing, is_bezier = classify_keyframe(kp)
            # Bezier handles are stored in (frame, value) space, exactly as the
            # graph editor holds them, so tweaks round-trip losslessly.
            hl = (kp.handle_left[0], kp.handle_left[1])
            hr = (kp.handle_right[0], kp.handle_right[1])
            keyframes.append((frame, value, tl_interp, tl_easing, hl, hr))
            last_frame = max(last_frame, frame)
        keyframes.sort(key=lambda k: k[0])

        # skip the export tag itself
        if custom_name in ("mc_export", "anim_export"):
            continue

        tracks.setdefault(track_key, []).append((ch_type, custom_name, keyframes))

    return tracks, last_frame


def build_animations(ctx):
    """
    Build animation clips.

    Blender keeps each object's animation in its own Action, so a scene where an
    empty, an armature and some props all animate on one shared timeline holds
    several Actions. Exporting those as separate clips means a player that only
    runs one clip animates just a single object (the classic "armature has no
    anims / length is only the empty loop" symptom).

    With `single_clip` (default), every animated object's tracks are merged into
    ONE clip on a shared timeline so playing that clip animates everything at
    once. Set it off to keep one clip per Action.
    """
    settings = ctx.settings
    clips = []

    if settings.include_animations:
        if settings.single_clip:
            merged = {}          # track_key → channel list
            last = 0.0
            for obj in ctx.objects:
                tracks, l = collect_action(obj, ctx)
                if not tracks:
                    continue
                last = max(last, l)
                # track keys are unique per target (node index / skel+bone), so
                # merging never collides.
                for key, channels in tracks.items():
                    merged.setdefault(key, []).extend(channels)
            if merged:
                clip_name = settings.model_name or ctx.scene_name or "animation"
                clips.append((clip_name, merged, last))
        else:
            for obj in ctx.objects:
                tracks, last = collect_action(obj, ctx)
                if tracks:
                    name = (obj.animation_data.action.name
                            if obj.animation_data and obj.animation_data.action else obj.name)
                    clips.append((name, tracks, last))

    w = BinWriter()
    w.u32(len(clips))
    for name, tracks, last in clips:
        w.u32(ctx.s(name))
        w.f32(ctx.fps)
        w.f32(last)
        w.u8(0)  # loop_mode: 0 = once (reserved for future use)
        w.u32(len(tracks))
        for (target_type, a, b), channels in tracks.items():
            w.u8(target_type)
            if target_type == TARGET_NODE:
                w.i32(a)          # node index
            else:
                w.i32(a)          # skeleton index
                w.i32(b)          # bone index
            w.u32(len(channels))
            for ch_type, custom_name, keyframes in channels:
                w.u8(ch_type)
                if ch_type == CH_CUSTOM:
                    w.u32(ctx.s(custom_name))
                w.u32(len(keyframes))
                for frame, value, tl_interp, tl_easing, hl, hr in keyframes:
                    w.f32(frame)
                    w.f32(value)
                    w.u8(tl_interp)
                    # Per-interp trailing payload:
                    #   EASE   → u32 easing string ref
                    #   BEZIER → f32×4 handle_left(x,y), handle_right(x,y)
                    #   STEP/LINEAR/CATMULL → nothing
                    if tl_interp == INTERP_EASE:
                        w.u32(ctx.s(tl_easing))
                    elif tl_interp == INTERP_BEZIER:
                        w.f32(hl[0]); w.f32(hl[1])
                        w.f32(hr[0]); w.f32(hr[1])
    return bytes(w)


# ═════════════════════════════════════════════════════════════════════════
#  Top-level serialization
# ═════════════════════════════════════════════════════════════════════════

def build_meta(ctx, scene, node_count, mesh_count, anim_count):
    w = BinWriter()
    w.u32(ctx.s(ctx.settings.model_name or scene.name))
    w.f32(ctx.fps)
    w.f32(float(scene.frame_end))
    w.i32(scene.frame_start)
    w.i32(scene.frame_end)
    w.u8(1 if ctx.settings.y_up else 0)      # 1 = Y-up, 0 = Z-up (Blender native)
    # Global root transform: seed the world matrix of every root node with this.
    # Carries the Z-up→Y-up conversion so it applies uniformly to rest AND
    # animated transforms (never baked into individual, animatable nodes).
    root_transform = YUP_CONVERSION if ctx.settings.y_up else Matrix.Identity(4)
    w.matrix4(root_transform)
    w.u32(ctx.s("MC Model Exporter (TLMDL) v%d.%d" % (VERSION_MAJOR, VERSION_MINOR)))
    w.u32(node_count)
    w.u32(mesh_count)
    w.u32(anim_count)
    return bytes(w)


def export_scene(context, settings, filepath):
    scene = context.scene
    fps = scene.render.fps / scene.render.fps_base
    ctx = ExportContext(settings, fps)
    ctx.scene_name = scene.name
    ctx.frame_end = scene.frame_end

    ordered, _ = collect_objects(context, settings)
    ctx.objects = ordered

    # Build in dependency order. NODE first assigns node indices + armature list.
    node_payload = build_nodes(ctx)
    # SKEL must precede MESH so skin weights can map bone names → indices.
    skel_payload = build_skeletons(ctx)
    mesh_payload, _node_mesh_map = build_meshes(ctx)
    matl_payload = build_materials(ctx)     # populated by build_meshes' material_ref calls
    texr_payload = build_textures(ctx)      # populated by build_materials' image_ref calls
    anim_payload = build_animations(ctx)

    anim_count = struct.unpack_from("<I", anim_payload, 0)[0]
    mesh_count = struct.unpack_from("<I", mesh_payload, 0)[0]
    meta_payload = build_meta(ctx, scene, len(ordered), mesh_count, anim_count)

    # String table is complete now; serialize it last-built, written first.
    strt_payload = ctx.strings.serialize()

    out = bytearray()
    out += MAGIC
    out += struct.pack("<BB", VERSION_MAJOR, VERSION_MINOR)
    out += struct.pack("<H", 0)   # global flags

    write_chunk(out, CHUNK_STRT, strt_payload)
    write_chunk(out, CHUNK_META, meta_payload)
    write_chunk(out, CHUNK_NODE, node_payload)
    write_chunk(out, CHUNK_SKEL, skel_payload)
    write_chunk(out, CHUNK_MESH, mesh_payload)
    write_chunk(out, CHUNK_MATL, matl_payload)
    write_chunk(out, CHUNK_TEXR, texr_payload)
    write_chunk(out, CHUNK_ANIM, anim_payload)
    write_chunk(out, CHUNK_END, b"")

    with open(filepath, "wb") as f:
        f.write(out)

    return {
        "nodes":     len(ordered),
        "meshes":    mesh_count,
        "materials": len(ctx.materials),
        "textures":  len(ctx.images),
        "skeletons": len(ctx.armatures),
        "anims":     anim_count,
        "bytes":     len(out),
    }


# ═════════════════════════════════════════════════════════════════════════
#  Operators
# ═════════════════════════════════════════════════════════════════════════

class MCMODEL_OT_Export(Operator):
    bl_idname = "mcmodel.export"
    bl_label = "Export TLMDL"
    bl_description = "Export the scene model + animations to a .tlmdl binary file"

    filepath: StringProperty(subtype="FILE_PATH")
    filter_glob: StringProperty(default="*.tlmdl", options={"HIDDEN"})

    def invoke(self, context, event):
        settings = context.scene.mcmodel_settings
        default = settings.model_name or context.scene.name or "model"
        self.filepath = default + ".tlmdl"
        context.window_manager.fileselect_add(self)
        return {"RUNNING_MODAL"}

    def execute(self, context):
        settings = context.scene.mcmodel_settings
        filepath = self.filepath
        if not filepath.lower().endswith(".tlmdl"):
            filepath += ".tlmdl"

        ordered, _ = collect_objects(context, settings)
        if not ordered:
            self.report({"WARNING"}, "Nothing to export for the chosen source mode.")
            return {"CANCELLED"}

        try:
            stats = export_scene(context, settings, filepath)
        except Exception as e:
            import traceback
            traceback.print_exc()
            self.report({"ERROR"}, f"Export failed: {e}")
            return {"CANCELLED"}

        kb = stats["bytes"] / 1024.0
        self.report(
            {"INFO"},
            "Exported %d nodes, %d meshes, %d mats, %d tex, %d skel, %d anim → %s (%.1f KB)"
            % (stats["nodes"], stats["meshes"], stats["materials"], stats["textures"],
               stats["skeletons"], stats["anims"], os.path.basename(filepath), kb),
        )
        return {"FINISHED"}


class MCMODEL_OT_TagSelected(Operator):
    bl_idname = "mcmodel.tag_selected"
    bl_label = "Tag Selected"
    bl_description = "Mark selected objects for export (used by the 'Tagged' source mode)"

    def execute(self, context):
        count = 0
        for obj in context.selected_objects:
            obj["mc_export"] = True
            count += 1
        self.report({"INFO"}, f"Tagged {count} object(s).")
        return {"FINISHED"}


class MCMODEL_OT_UntagSelected(Operator):
    bl_idname = "mcmodel.untag_selected"
    bl_label = "Untag Selected"
    bl_description = "Remove the export tag from selected objects"

    def execute(self, context):
        count = 0
        for obj in context.selected_objects:
            if "mc_export" in obj:
                del obj["mc_export"]
                count += 1
        self.report({"INFO"}, f"Untagged {count} object(s).")
        return {"FINISHED"}


class MCMODEL_OT_UntagAll(Operator):
    bl_idname = "mcmodel.untag_all"
    bl_label = "Clear All Tags"
    bl_description = "Remove the export tag from every object in the scene"

    def execute(self, context):
        count = 0
        for obj in context.scene.objects:
            if "mc_export" in obj:
                del obj["mc_export"]
                count += 1
        self.report({"INFO"}, f"Cleared tags from {count} object(s).")
        return {"FINISHED"}


# ═════════════════════════════════════════════════════════════════════════
#  Settings
# ═════════════════════════════════════════════════════════════════════════

class McModelSettings(PropertyGroup):
    model_name: StringProperty(
        name="Model Name",
        description="Name stored in the file meta block (also the default filename)",
        default="",
    )
    export_mode: EnumProperty(
        name="Source",
        description="Which objects to export",
        items=[
            ('SELECTED', "Selected", "Export selected objects (+ their parents)"),
            ('TAGGED',   "Tagged",   "Export objects tagged via the buttons below"),
            ('ALL',      "All",      "Export every object in the scene"),
        ],
        default='SELECTED',
    )
    apply_modifiers: BoolProperty(
        name="Apply Modifiers",
        description="Evaluate modifiers (mirror, subsurf, etc.) before exporting geometry",
        default=True,
    )
    include_animations: BoolProperty(
        name="Include Animations",
        description="Export object + pose-bone F-curve animation data",
        default=True,
    )
    single_clip: BoolProperty(
        name="Combine Into One Clip",
        description="Merge every object's animation onto one shared-timeline clip "
                    "(so a player that runs one clip animates the whole scene). "
                    "Turn off to export one clip per Blender Action",
        default=True,
    )
    embed_textures: BoolProperty(
        name="Embed Textures",
        description="Store image bytes inside the file instead of external paths",
        default=True,
    )
    y_up: BoolProperty(
        name="Y-Up (Minecraft)",
        description="Convert from Blender's Z-up to Y-up so models face the right way in game",
        default=True,
    )
    max_influences: IntProperty(
        name="Max Bone Influences",
        description="Maximum number of bones weighted per vertex",
        default=4, min=1, max=8,
    )


# ═════════════════════════════════════════════════════════════════════════
#  UI
# ═════════════════════════════════════════════════════════════════════════

class MCMODEL_PT_MainPanel(Panel):
    bl_label = "MC Model Exporter"
    bl_idname = "MCMODEL_PT_main"
    bl_space_type = "VIEW_3D"
    bl_region_type = "UI"
    bl_category = "MC Model"

    def draw(self, context):
        layout = self.layout
        scene = context.scene
        settings = scene.mcmodel_settings

        box = layout.box()
        box.label(text="Settings", icon="PREFERENCES")
        box.prop(settings, "model_name", text="Name", icon="MESH_DATA")
        box.prop(settings, "export_mode", text="Source")
        box.label(text=f"FPS: {scene.render.fps}/{scene.render.fps_base}", icon="TIME")

        box = layout.box()
        box.label(text="Options", icon="MODIFIER")
        box.prop(settings, "apply_modifiers")
        box.prop(settings, "include_animations")
        row = box.row()
        row.enabled = settings.include_animations
        row.prop(settings, "single_clip")
        box.prop(settings, "embed_textures")
        box.prop(settings, "y_up")
        box.prop(settings, "max_influences")

        if settings.export_mode == 'TAGGED':
            box = layout.box()
            box.label(text="Tag Objects", icon="BOOKMARKS")
            row = box.row(align=True)
            row.operator("mcmodel.tag_selected", icon="ADD", text="Tag")
            row.operator("mcmodel.untag_selected", icon="REMOVE", text="Untag")
            box.operator("mcmodel.untag_all", icon="X", text="Clear All Tags")
            tagged = [o for o in scene.objects if o.get("mc_export", False)]
            box.label(text=f"Tagged: {len(tagged)}", icon="CHECKMARK")

        layout.separator()
        layout.operator("mcmodel.export", icon="EXPORT", text="Export .tlmdl")


# ═════════════════════════════════════════════════════════════════════════
#  Registration
# ═════════════════════════════════════════════════════════════════════════

CLASSES = [
    McModelSettings,
    MCMODEL_OT_Export,
    MCMODEL_OT_TagSelected,
    MCMODEL_OT_UntagSelected,
    MCMODEL_OT_UntagAll,
    MCMODEL_PT_MainPanel,
]


def register():
    for cls in CLASSES:
        bpy.utils.register_class(cls)
    bpy.types.Scene.mcmodel_settings = bpy.props.PointerProperty(type=McModelSettings)


def unregister():
    for cls in reversed(CLASSES):
        bpy.utils.unregister_class(cls)
    del bpy.types.Scene.mcmodel_settings


if __name__ == "__main__":
    register()
