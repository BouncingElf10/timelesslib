# TLMDL — TimelessLib Model + Animation Format (v2.1)

A compact, chunked, little-endian binary container that replaces the old
JSON animation export. It stores a full FBX-style scene: node hierarchy,
meshes, materials, textures, skeletons, per-vertex skinning, and animation —
with graph-editor-faithful interpolation.

Produced by `MC MODEL EXPORTER.py` (Blender add-on). This document is the
authoritative byte-level spec for writing a matching reader (e.g. a Java
importer for TimelessLib).

---

## 1. Conventions

- **Endianness:** little-endian, always.
- **Primitives:** `u8 u16 u32 i32` integers; `f32` (IEEE-754 single); `f64` double.
- **vec2/3/4:** 2/3/4 consecutive `f32`.
- **quat:** 4 × `f32` in **(w, x, y, z)** order (Blender native).
- **matrix4:** 16 × `f32`, **row-major** (`m[row][col]`, row 0 first).
- **string:** never inlined — always a `u32` index into the string table
  (see STRT). `0xFFFFFFFF` (`NULL_INDEX`) means "no string / null".
- **color_u8:** 4 × `u8` RGBA (0–255), from linear float × 255 rounded.

All angles/positions for meshes are in the object's **local space**; node
transforms place them in the world.

---

## 2. File layout

```
Header
Chunk STRT      (string table — always first so refs resolve)
Chunk META
Chunk NODE
Chunk SKEL
Chunk MESH
Chunk MATL
Chunk TEXR
Chunk ANIM
Chunk "END "    (empty payload — terminator)
```

### Header (9 bytes)

| field         | type    | value            |
|---------------|---------|------------------|
| magic         | 5 bytes | `TLMDL`          |
| version_major | u8      | `2`              |
| version_minor | u8      | `1`              |
| flags         | u16     | reserved, `0`    |

### Chunk framing

Every chunk is:

| field       | type    | notes                                             |
|-------------|---------|---------------------------------------------------|
| id          | 4 bytes | ASCII, e.g. `MESH`, `ANIM`, `END ` (note space)   |
| compression | u8      | `0` = raw, `1` = zlib (RFC 1950)                  |
| stored_size | u32     | byte length of the payload **as stored**          |
| raw_size    | u32     | uncompressed payload length                       |
| payload     | bytes   | `stored_size` bytes; inflate if `compression==1`  |

A reader loops: read framing, slice `stored_size` bytes, inflate if needed,
dispatch on `id`, stop after `END `. Unknown chunk ids should be skipped
(forward-compatible).

---

## 3. STRT — string table

| field | type | notes |
|-------|------|-------|
| count | u32  | number of strings |
| per string: length | u32 | UTF-8 byte length |
| per string: bytes  | …   | UTF-8, no terminator |

Everything else references strings by their 0-based index here.

---

## 4. META — scene metadata

| field           | type | notes                               |
|-----------------|------|-------------------------------------|
| name            | str  | model name                          |
| fps             | f32  | scene frames per second             |
| duration_frames | f32  | scene `frame_end`                   |
| frame_start     | i32  |                                     |
| frame_end       | i32  |                                     |
| up_axis         | u8   | `1` = Y-up, `0` = Z-up (Blender native) |
| root_transform  | matrix4 | global world-space seed for root nodes |
| generator       | str  | exporter name + version             |
| node_count      | u32  |                                     |
| mesh_count      | u32  |                                     |
| anim_count      | u32  |                                     |

**`root_transform` (important).** This is the ONLY place the Z-up→Y-up
conversion lives — a −90° X rotation when `up_axis==1`, else identity. It is
**not** baked into any node, so animation channels never conflict with it.
Seed the world matrix of every root node (`parent_index == -1`) with
`root_transform` (see §11 for the full formula). Vertex data is in object-local
space and untouched.

---

## 5. NODE — scene graph

| field | type | notes |
|-------|------|-------|
| count | u32  | number of nodes |

Then per node (parents always precede children):

| field         | type    | notes                                        |
|---------------|---------|----------------------------------------------|
| name          | str     |                                              |
| parent_index  | i32     | `-1` = root                                  |
| type          | u8      | 0 EMPTY · 1 MESH · 2 ARMATURE · 3 LIGHT · 4 CAMERA |
| translation   | vec3    | **matrix_basis** local (what animation drives) |
| rotation      | quat    | matrix_basis local (w,x,y,z)                 |
| scale         | vec3    | matrix_basis local                           |
| parent_inverse| matrix4 | Blender `matrix_parent_inverse` (row-major)  |
| skeleton_index| i32     | index into SKEL if type==ARMATURE, else `-1` |

The stored T/R/S is the node's **native basis transform** — identical to the
values the animation channels (§10) animate — so a node's rest pose and its
animated pose are in the same space. `parent_inverse` is the constant offset
Blender inserts when you parent an object (Ctrl+P); it is usually **not**
identity. See §11 for how to combine these into a world matrix.

Mesh ↔ node linkage is stored on the **mesh side** (see MESH).

---

## 6. MESH — geometry

| field      | type | notes |
|------------|------|-------|
| mesh_count | u32  |       |

Then per mesh:

| field       | type | notes                                    |
|-------------|------|------------------------------------------|
| node_index  | i32  | owning NODE this mesh is attached to     |

...immediately followed by the mesh body:

| field         | type | notes |
|---------------|------|-------|
| name          | str  |       |
| vertex_count  | u32  |       |
| uv_layers     | u8   | number of UV layers |
| color_layers  | u8   | number of vertex-colour layers |
| flags         | u8   | bit0 normals (always 1) · bit2 skinned |
| max_influences| u8   | bones per vertex if skinned, else 0 |
| skeleton_index| i32  | SKEL index bound to this mesh, or `-1` |

**Vertex attribute blocks** (in this exact order; each is `vertex_count` long):

1. **positions** — `vertex_count` × vec3 (local space)
2. **normals** — `vertex_count` × vec3
3. **uv layers** — for each of `uv_layers`: `vertex_count` × vec2
4. **colour layers** — for each of `color_layers`: `vertex_count` × color_u8
5. **skin** (only if bit2 set) — `vertex_count` × (`max_influences` ×
   { bone `u16`, weight `f32` }). Influences are weight-sorted descending and
   normalised; unused slots are `{0, 0.0}`.

**Material slot table:**

| field       | type | notes |
|-------------|------|-------|
| slot_count  | u16  |       |
| slot → global | i32 (×slot_count) | maps a local slot to a MATL index (`-1` = none) |

**Triangles:**

| field       | type | notes |
|-------------|------|-------|
| tri_count   | u32  |       |
| index_size  | u8   | `2` if vertex_count ≤ 65535 else `4` |

Then per triangle:

| field          | type              | notes |
|----------------|-------------------|-------|
| a, b, c        | u16 or u32 (×3)   | vertex indices (width = index_size) |
| material_slot  | u16               | index into this mesh's slot table |

Geometry is pre-triangulated and vertices are de-duplicated by
(position, normal, all UVs, all colours).

---

## 7. MATL — materials

| field          | type | notes |
|----------------|------|-------|
| material_count | u32  |       |

Per material (from the Principled BSDF):

| field            | type | notes |
|------------------|------|-------|
| name             | str  |       |
| base_color       | vec4 | RGBA  |
| metallic         | f32  |       |
| roughness        | f32  |       |
| emissive         | vec3 | RGB   |
| emissive_strength| f32  |       |
| alpha            | f32  |       |
| flags            | u8   | bit0 double-sided · bit1 transparent |
| binding_count    | u8   |       |

Per texture binding:

| field       | type | notes |
|-------------|------|-------|
| usage       | u8   | 0 BASE_COLOR · 1 NORMAL · 2 METALLIC_ROUGHNESS · 3 EMISSIVE · 4 OCCLUSION · 5 ROUGHNESS · 6 METALLIC |
| texture_idx | i32  | index into TEXR (`-1` = none) |

---

## 8. TEXR — textures / images

| field         | type | notes |
|---------------|------|-------|
| texture_count | u32  |       |

Per texture:

| field    | type | notes |
|----------|------|-------|
| name     | str  | image datablock name |
| wrap_u   | u8   | 0 repeat · 1 clamp · 2 mirror |
| wrap_v   | u8   | same |
| filter   | u8   | 0 nearest · 1 linear |
| source   | u8   | `0` external, `1` embedded |

If `source == 0` (external):

| field | type | notes |
|-------|------|-------|
| path  | str  | absolute file path |

If `source == 1` (embedded):

| field     | type  | notes |
|-----------|-------|-------|
| filename  | str   | original file name |
| byte_len  | u32   | length of the encoded image |
| data      | bytes | the encoded image file (usually PNG) verbatim |

---

## 9. SKEL — skeletons / armatures

| field           | type | notes |
|-----------------|------|-------|
| skeleton_count  | u32  |       |

Per skeleton:

| field | type | notes |
|-------|------|-------|
| name  | str  |       |
| bone_count | u32 | |

Per bone (parents precede children):

| field         | type    | notes |
|---------------|---------|-------|
| name          | str     |       |
| parent_index  | i32     | `-1` = root bone |
| translation   | vec3    | rest, relative to parent bone |
| rotation      | quat    | rest |
| scale         | vec3    | rest |
| inverse_bind  | matrix4 | armature-space inverse bind (row-major) |
| length        | f32     | bone length (visual only) |

Skin `bone` indices in MESH refer to a bone's position in this list.

---

## 10. ANIM — animation clips

| field          | type | notes |
|----------------|------|-------|
| animation_count| u32  |       |

**Clips vs. tracks (READ THIS).** Blender stores each object's animation in its
own Action. By default the exporter **merges every animated object onto one
shared-timeline clip**, so `animation_count == 1` and that single clip contains
*all* tracks — the empty's node track, every bone track, the props' node
tracks. A player should run that one clip and apply **all** its tracks at once.
If the exporter's "Combine Into One Clip" option is off, you instead get one
clip per Action, and a player that runs only clip 0 will animate just one object
(this is the "armature has no anims / length is only the empty's loop" bug).

Per clip:

| field           | type | notes |
|-----------------|------|-------|
| name            | str  | action name |
| fps             | f32  |       |
| duration_frames | f32  | last keyframe |
| loop_mode       | u8   | 0 once (reserved) |
| track_count     | u32  |       |

Per track (one animated target):

| field        | type | notes |
|--------------|------|-------|
| target_type  | u8   | 0 NODE · 1 BONE |
| target refs  | …    | NODE: `i32 node_index`. BONE: `i32 skeleton_index`, `i32 bone_index` |
| channel_count| u32  |       |

Per channel:

| field        | type | notes |
|--------------|------|-------|
| channel_type | u8   | see table below |
| custom_name  | str  | **only if** channel_type == 255 |
| keyframe_count| u32 |       |

**channel_type values:**

| id | meaning        | id | meaning        |
|----|----------------|----|----------------|
| 0  | location.x     | 7  | rotation_euler.x |
| 1  | location.y     | 8  | rotation_euler.y |
| 2  | location.z     | 9  | rotation_euler.z |
| 3  | rotation_quat.w| 10 | scale.x        |
| 4  | rotation_quat.x| 11 | scale.y        |
| 5  | rotation_quat.y| 12 | scale.z        |
| 6  | rotation_quat.z| 255| custom (name follows) |

Per keyframe:

| field  | type | notes |
|--------|------|-------|
| frame  | f32  |       |
| value  | f32  |       |
| interp | u8   | 0 STEP · 1 LINEAR · 2 EASE · 3 CATMULL · 4 BEZIER |

Then a **per-interp trailing payload**:

| interp   | trailing bytes |
|----------|----------------|
| 0 STEP   | *(none)* |
| 1 LINEAR | *(none)* |
| 2 EASE   | `u32 easing_name` — string ref to an `Easing.java` field name |
| 3 CATMULL| *(none)* |
| 4 BEZIER | `f32 handle_left_x, f32 handle_left_y, f32 handle_right_x, f32 handle_right_y` |

### Interpolation fidelity (graph-editor faithful)

- **STEP / LINEAR** map 1:1 from Blender CONSTANT / LINEAR.
- **EASE** is used for Blender's equation curves (Sine, Quad, Cubic, Quart,
  Quint, Expo, Circ, Back, Bounce, Elastic × In/Out/InOut). These *are* the
  easings.net functions, identical to `Easing.java`, so they round-trip with
  zero loss. The easing name is carried as a string.
- **BEZIER** carries the raw graph-editor handles in (frame, value) space.
  A segment from keyframe A→B is the cubic Bézier with control points
  `P0=(A.frame, A.value)`, `P1=A.handle_right`, `P2=B.handle_left`,
  `P3=(B.frame, B.value)`. To evaluate at a frame `f`, solve the x-cubic for
  parameter `t` (Newton/bisection), then evaluate the y-cubic. This means any
  handle you drag in Blender is reflected exactly in the file.

An importer that doesn't yet support BEZIER can fall back to treating those
keyframes as LINEAR without breaking parsing (the handle floats are fixed-size
and skippable).

Channel values are in **native Blender space** — the same values you see in the
N-panel. They drive `matrix_basis` (see §11), *not* the world transform, so they
must be combined with `parent_inverse` and the parent chain, never applied as
a final world transform.

---

## 11. Reconstruction (READ THIS — importer transform math)

The exporter deliberately keeps **rest and animated transforms in the same
space** and applies the axis conversion **once, globally**. An importer MUST
follow the same composition or animated/parented objects will be misplaced.

### 11.1 Node world matrix

For each node, per frame, build the animated local TRS from the channels
(falling back to the node's stored rest T/R/S for any component with no
channel), then:

```
localBasis = compose(T, R, S)                      // from channels or rest
nodeLocal  = parent_inverse ⋅ localBasis           // matrix multiply
world(node) =
    (parent_index < 0) ? root_transform ⋅ nodeLocal
                       : world(parent) ⋅ nodeLocal
```

This is exactly Blender's rule
`matrix_world = parent.matrix_world ⋅ matrix_parent_inverse ⋅ matrix_basis`,
with `root_transform` seeding the roots (that is where Y-up conversion enters).

`compose(...)` must build the local matrix from whichever channels are present:

- **Rotation may arrive as EULER (channel types 7/8/9), not just quaternion
  (3/6).** An empty rotating in Blender is usually keyed as `rotation_euler`.
  If you only implement quaternion channels, that rotation is silently dropped
  and neither the empty nor its children rotate. Build rotation from the euler
  triple (Blender default order **XYZ**, i.e. `Rz · Ry · Rx`) when euler
  channels exist; otherwise from the quaternion channels; otherwise the rest.
- For any component with no channel, fall back to the node's rest T/R/S.

**Common mistakes that produce the "objects don't rotate with their parent /
way down" bugs:**
- Only handling quaternion rotation channels and ignoring euler ones (see above).
- Applying channel values as a world transform (skipping `parent_inverse` and
  the parent chain).
- **Not recomposing children each frame.** A child inherits its parent's
  *animated* world matrix, so `world(child)` must be recomputed every frame from
  `world(parent)`. Caching child world matrices at bind time means a child never
  follows its animated parent.
- Baking Y-up conversion into node rest transforms — then an *animated* node
  overwrites its transform with raw Z-up channel values and the whole subtree
  loses the conversion. Always apply conversion via `root_transform` only.

### 11.2 Skeleton / bone matrices

Bone channels (`target_type == BONE`) are **pose offsets in bone-local space**
(Blender's `pose_bone.matrix_basis`), relative to the bone's rest. Compose:

```
poseBasis   = compose(bone channels, default identity)     // NOT the rest
boneLocal   = rest_local(bone) ⋅ poseBasis                 // rest_local = stored bone T/R/S
boneArmature(bone) =
    (parent < 0) ? boneLocal
                 : boneArmature(parent) ⋅ boneLocal
skinMatrix(bone) = boneArmature(bone) ⋅ inverse_bind(bone)
```

Then a skinned vertex is
`Σ weightᵢ ⋅ skinMatrix(boneᵢ) ⋅ vertexPosition`, and the whole skinned mesh is
finally placed by its owning node's world matrix (§11.1).

**"Armature isn't moving" checklist:**
- Make sure the armature object is actually in the file (v2.1 auto-includes
  armatures referenced by a skinned mesh's Armature modifier, even if it wasn't
  selected). Look for a `BONE` track in ANIM.
- `poseBasis` must default to **identity** when a channel is absent — not to the
  bone's rest — because it is an *offset* applied on top of `rest_local`.
- Apply `rest_local ⋅ poseBasis` in that order, then multiply by `inverse_bind`
  only at the end for skinning (never for the bone's own hierarchy step).
