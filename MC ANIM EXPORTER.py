"""
mc_anim_exporter.py
Blender Addon — Minecraft / TimelessLib Animation Exporter

Exports tagged objects' F-curve data to a structured JSON file.
Tag objects via the "MC Anim" panel in the N-sidebar (3D Viewport).

Install:  Edit > Preferences > Add-ons > Install > select this file > enable
Usage:    Select objects, open N-panel > "MC Anim" tab, tag & export
"""

bl_info = {
    "name":        "MC Anim Exporter",
    "author":      "Your Pipeline",
    "version":     (1, 0, 0),
    "blender":     (4, 0, 0),
    "location":    "View3D > Sidebar > MC Anim",
    "description": "Export tagged object animations to JSON for TimelessLib",
    "category":    "Animation",
}

import bpy
import json
import os
from bpy.props import BoolProperty, StringProperty
from bpy.types import Panel, Operator, PropertyGroup


# ─────────────────────────────────────────────
#  Constants
# ─────────────────────────────────────────────

# Maps (data_path, array_index) → normalized channel key
# Anything NOT in this table falls through to generic normalization
KNOWN_PATHS = {
    ("location",            0): "location.x",
    ("location",            1): "location.y",
    ("location",            2): "location.z",
    ("rotation_euler",      0): "rotation_euler.x",
    ("rotation_euler",      1): "rotation_euler.y",
    ("rotation_euler",      2): "rotation_euler.z",
    ("rotation_quaternion", 0): "rotation_quat.w",
    ("rotation_quaternion", 1): "rotation_quat.x",
    ("rotation_quaternion", 2): "rotation_quat.y",
    ("rotation_quaternion", 3): "rotation_quat.z",
    ("scale",               0): "scale.x",
    ("scale",               1): "scale.y",
    ("scale",               2): "scale.z",
}

# Maps (blender_interpolation, blender_easing) → (timelesslib_interpolation, timelesslib_easing)
# timelesslib_easing is None when interpolation is STEP or LINEAR (no easing needed)
#
# TimelessLib interpolation modes: STEP, LINEAR, EASE, CATMULL
# TimelessLib easing constants match Easing.java field names exactly
INTERP_MAP = {
    # Constant / step
    ("CONSTANT", "NONE"):        ("STEP",   None),
    ("CONSTANT", "AUTO"):        ("STEP",   None),
    ("CONSTANT", "EASE_IN"):     ("STEP",   None),
    ("CONSTANT", "EASE_OUT"):    ("STEP",   None),
    ("CONSTANT", "EASE_IN_OUT"): ("STEP",   None),

    # Linear
    ("LINEAR", "NONE"):          ("LINEAR", None),
    ("LINEAR", "AUTO"):          ("LINEAR", None),

    # Bezier AUTO — Blender uses smooth auto handles, closest match is EASE_IN_OUT_SINE
    ("BEZIER", "AUTO"):          ("EASE",   "EASE_IN_OUT_SINE"),
    ("BEZIER", "EASE_IN"):       ("EASE",   "EASE_IN_SINE"),
    ("BEZIER", "EASE_OUT"):      ("EASE",   "EASE_OUT_SINE"),
    ("BEZIER", "EASE_IN_OUT"):   ("EASE",   "EASE_IN_OUT_SINE"),
    ("BEZIER", "NONE"):          ("LINEAR", None),

    # Sine
    ("SINE", "EASE_IN"):         ("EASE",   "EASE_IN_SINE"),
    ("SINE", "EASE_OUT"):        ("EASE",   "EASE_OUT_SINE"),
    ("SINE", "EASE_IN_OUT"):     ("EASE",   "EASE_IN_OUT_SINE"),
    ("SINE", "AUTO"):            ("EASE",   "EASE_IN_OUT_SINE"),

    # Quad
    ("QUAD", "EASE_IN"):         ("EASE",   "EASE_IN_QUAD"),
    ("QUAD", "EASE_OUT"):        ("EASE",   "EASE_OUT_QUAD"),
    ("QUAD", "EASE_IN_OUT"):     ("EASE",   "EASE_IN_OUT_QUAD"),
    ("QUAD", "AUTO"):            ("EASE",   "EASE_IN_OUT_QUAD"),

    # Cubic
    ("CUBIC", "EASE_IN"):        ("EASE",   "EASE_IN_CUBIC"),
    ("CUBIC", "EASE_OUT"):       ("EASE",   "EASE_OUT_CUBIC"),
    ("CUBIC", "EASE_IN_OUT"):    ("EASE",   "EASE_IN_OUT_CUBIC"),
    ("CUBIC", "AUTO"):           ("EASE",   "EASE_IN_OUT_CUBIC"),

    # Quart
    ("QUART", "EASE_IN"):        ("EASE",   "EASE_IN_QUART"),
    ("QUART", "EASE_OUT"):       ("EASE",   "EASE_OUT_QUART"),
    ("QUART", "EASE_IN_OUT"):    ("EASE",   "EASE_IN_OUT_QUART"),
    ("QUART", "AUTO"):           ("EASE",   "EASE_IN_OUT_QUART"),

    # Quint
    ("QUINT", "EASE_IN"):        ("EASE",   "EASE_IN_QUINT"),
    ("QUINT", "EASE_OUT"):       ("EASE",   "EASE_OUT_QUINT"),
    ("QUINT", "EASE_IN_OUT"):    ("EASE",   "EASE_IN_OUT_QUINT"),
    ("QUINT", "AUTO"):           ("EASE",   "EASE_IN_OUT_QUINT"),

    # Expo
    ("EXPO", "EASE_IN"):         ("EASE",   "EASE_IN_EXPO"),
    ("EXPO", "EASE_OUT"):        ("EASE",   "EASE_OUT_EXPO"),
    ("EXPO", "EASE_IN_OUT"):     ("EASE",   "EASE_IN_OUT_EXPO"),
    ("EXPO", "AUTO"):            ("EASE",   "EASE_IN_OUT_EXPO"),

    # Circ
    ("CIRC", "EASE_IN"):         ("EASE",   "EASE_IN_CIRC"),
    ("CIRC", "EASE_OUT"):        ("EASE",   "EASE_OUT_CIRC"),
    ("CIRC", "EASE_IN_OUT"):     ("EASE",   "EASE_IN_OUT_CIRC"),
    ("CIRC", "AUTO"):            ("EASE",   "EASE_IN_OUT_CIRC"),

    # Back
    ("BACK", "EASE_IN"):         ("EASE",   "EASE_IN_BACK"),
    ("BACK", "EASE_OUT"):        ("EASE",   "EASE_OUT_BACK"),
    ("BACK", "EASE_IN_OUT"):     ("EASE",   "EASE_IN_OUT_BACK"),
    ("BACK", "AUTO"):            ("EASE",   "EASE_IN_OUT_BACK"),

    # Bounce
    ("BOUNCE", "EASE_IN"):       ("EASE",   "EASE_IN_BOUNCE"),
    ("BOUNCE", "EASE_OUT"):      ("EASE",   "EASE_OUT_BOUNCE"),
    ("BOUNCE", "EASE_IN_OUT"):   ("EASE",   "EASE_IN_OUT_BOUNCE"),
    ("BOUNCE", "AUTO"):          ("EASE",   "EASE_IN_OUT_BOUNCE"),

    # Elastic
    ("ELASTIC", "EASE_IN"):      ("EASE",   "EASE_IN_ELASTIC"),
    ("ELASTIC", "EASE_OUT"):     ("EASE",   "EASE_OUT_ELASTIC"),
    ("ELASTIC", "EASE_IN_OUT"):  ("EASE",   "EASE_IN_OUT_ELASTIC"),
    ("ELASTIC", "AUTO"):         ("EASE",   "EASE_IN_OUT_ELASTIC"),
}


# ─────────────────────────────────────────────
#  Helpers
# ─────────────────────────────────────────────

def normalize_channel_key(data_path: str, array_index: int) -> str:
    """
    Convert a raw Blender F-curve (data_path, array_index) to a
    clean, predictable channel key string.

    Examples:
      ("location", 0)              → "location.x"
      ('["laser_height"]', -1)     → "custom.laser_height"
      ("rotation_euler", 2)        → "rotation_euler.z"
      ("some_other_prop", 0)       → "some_other_prop.0"
    """
    # 1. Known standard transforms
    key = KNOWN_PATHS.get((data_path, array_index))
    if key:
        return key

    # 2. Custom properties — Blender uses '["prop_name"]' syntax
    if data_path.startswith('["') and data_path.endswith('"]'):
        prop_name = data_path[2:-2]  # strip [" and "]
        return f"custom.{prop_name}"

    # 3. Nested custom props e.g. 'pose.bones["Bone"]["prop"]'
    if '"]["' in data_path:
        # grab last ["prop"] segment
        last_prop = data_path.rsplit('"]["', 1)[-1].rstrip('"]')
        return f"custom.{last_prop}"

    # 4. Fallback — sanitize path and append index
    safe_path = data_path.replace('"', '').replace("'", '').replace(' ', '_')
    suffix = ["x", "y", "z", "w"][array_index] if 0 <= array_index <= 3 else str(array_index)
    return f"{safe_path}.{suffix}"


def map_interpolation(interpolation: str, easing: str) -> tuple:
    """
    Map a Blender (interpolation, easing) pair to a (timelesslib_interp, timelesslib_easing) tuple.
    timelesslib_easing is None for STEP and LINEAR modes.
    Falls back gracefully for any unknown combination.
    """
    result = INTERP_MAP.get((interpolation, easing))
    if result:
        return result
    # Fallback by interpolation type alone
    if interpolation == "CONSTANT":
        return ("STEP", None)
    if interpolation == "LINEAR":
        return ("LINEAR", None)
    # Any bezier-family unknown variant → smooth ease
    return ("EASE", "EASE_IN_OUT_SINE")


def get_tagged_objects(scene):
    """Yield all scene objects that have been tagged for export."""
    for obj in scene.objects:
        if obj.get("anim_export", False):
            yield obj


def get_fcurves(action):
    """
    Compatibility shim: yield all F-curves from an action.

    Blender 5.0+ restructured actions into a layered format:
        action.layers[i].strips[j].channelbags[k].fcurves
    Older Blender exposed them directly on action.fcurves.
    This function handles both transparently.
    """
    if hasattr(action, 'layers') and action.layers:
        for layer in action.layers:
            for strip in layer.strips:
                if hasattr(strip, 'channelbags'):
                    for channelbag in strip.channelbags:
                        yield from channelbag.fcurves
    elif hasattr(action, 'fcurves'):
        yield from action.fcurves


def export_object(obj, fps: float):
    """
    Extract all animated F-curves from an object's active action.
    Returns a dict ready for JSON serialisation, or None if no action.
    """
    action = obj.animation_data.action if obj.animation_data else None
    if not action:
        return None

    channels = {}

    for fcurve in get_fcurves(action):
        if not fcurve.keyframe_points:
            continue

        channel_key = normalize_channel_key(fcurve.data_path, fcurve.array_index)

        keyframes = []
        for kp in fcurve.keyframe_points:
            frame  = kp.co[0]
            value  = kp.co[1]
            interp = kp.interpolation          # e.g. "BEZIER", "LINEAR"
            easing = kp.easing                 # e.g. "EASE_IN", "AUTO"

            tl_interp, tl_easing = map_interpolation(interp, easing)
            kf_entry = {
                "frame": int(frame) if frame == int(frame) else round(frame, 2),
                "value": round(value, 4),
                "interp": tl_interp,
            }
            if tl_easing is not None:
                kf_entry["easing"] = tl_easing
            keyframes.append(kf_entry)

        # Sort by frame (Blender usually keeps them sorted, but be safe)
        keyframes.sort(key=lambda k: k["frame"])

        # Skip the export tag itself — it's not animation data
        if channel_key == "custom.anim_export":
            continue

        # Prune constant channels (every keyframe has the same value)
        unique_values = {kf["value"] for kf in keyframes}
        if len(unique_values) == 1:
            continue

        channels[channel_key] = keyframes

    if not channels:
        return None

    return {"channels": channels}


def compute_duration(objects_data: dict) -> float:
    """Find the last keyframe frame across all exported channels."""
    last_frame = 0.0
    for obj_data in objects_data.values():
        for keyframes in obj_data["channels"].values():
            if keyframes:
                last_frame = max(last_frame, keyframes[-1]["frame"])
    return last_frame


# ─────────────────────────────────────────────
#  Operator — Run Export
# ─────────────────────────────────────────────

class MCANIM_OT_Export(Operator):
    bl_idname  = "mcanim.export"
    bl_label   = "Export Animation JSON"
    bl_description = "Export all tagged objects' animations to a JSON file"

    filepath: StringProperty(subtype="FILE_PATH")

    # File browser filter
    filter_glob: StringProperty(default="*.json", options={"HIDDEN"})

    def invoke(self, context, event):
        # Pre-fill filename with the action name if available
        scene = context.scene
        default_name = scene.mcanim_settings.anim_name or "animation"
        self.filepath = default_name + ".json"
        context.window_manager.fileselect_add(self)
        return {"RUNNING_MODAL"}

    def execute(self, context):
        scene    = context.scene
        settings = scene.mcanim_settings
        fps      = scene.render.fps / scene.render.fps_base

        tagged = list(get_tagged_objects(scene))
        if not tagged:
            self.report({"WARNING"}, "No objects are tagged for export.")
            return {"CANCELLED"}

        objects_data = {}
        skipped      = []

        for obj in tagged:
            result = export_object(obj, fps)
            if result:
                objects_data[obj.name] = result
            else:
                skipped.append(obj.name)

        if not objects_data:
            self.report({"WARNING"}, "Tagged objects have no animation data.")
            return {"CANCELLED"}

        duration = compute_duration(objects_data)

        output = {
            "meta": {
                "name":             settings.anim_name or scene.name,
                "fps":              fps,
                "duration_frames":  duration,
                "version":          "1.0",
                "blender_scene":    scene.name,
            },
            "objects": objects_data,
        }

        # Ensure .json extension
        filepath = self.filepath
        if not filepath.lower().endswith(".json"):
            filepath += ".json"

        try:
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(output, f, indent=2)
        except OSError as e:
            self.report({"ERROR"}, f"Could not write file: {e}")
            return {"CANCELLED"}

        obj_count = len(objects_data)
        ch_count  = sum(len(o["channels"]) for o in objects_data.values())
        msg = f"Exported {obj_count} object(s), {ch_count} channel(s) → {os.path.basename(filepath)}"
        if skipped:
            msg += f"  (skipped — no action: {', '.join(skipped)})"
        self.report({"INFO"}, msg)
        return {"FINISHED"}


# ─────────────────────────────────────────────
#  Operator — Quick-tag selected objects
# ─────────────────────────────────────────────

class MCANIM_OT_TagSelected(Operator):
    bl_idname  = "mcanim.tag_selected"
    bl_label   = "Tag Selected"
    bl_description = "Mark all selected objects for animation export"

    def execute(self, context):
        count = 0
        for obj in context.selected_objects:
            obj["anim_export"] = True
            count += 1
        self.report({"INFO"}, f"Tagged {count} object(s) for export.")
        return {"FINISHED"}


class MCANIM_OT_UntagSelected(Operator):
    bl_idname  = "mcanim.untag_selected"
    bl_label   = "Untag Selected"
    bl_description = "Remove animation export tag from all selected objects"

    def execute(self, context):
        count = 0
        for obj in context.selected_objects:
            if "anim_export" in obj:
                del obj["anim_export"]
                count += 1
        self.report({"INFO"}, f"Untagged {count} object(s).")
        return {"FINISHED"}


class MCANIM_OT_UntagAll(Operator):
    bl_idname  = "mcanim.untag_all"
    bl_label   = "Clear All Tags"
    bl_description = "Remove animation export tag from every object in the scene"

    def execute(self, context):
        count = 0
        for obj in context.scene.objects:
            if "anim_export" in obj:
                del obj["anim_export"]
                count += 1
        self.report({"INFO"}, f"Cleared tags from {count} object(s).")
        return {"FINISHED"}


# ─────────────────────────────────────────────
#  Scene-level Settings
# ─────────────────────────────────────────────

class McAnimSettings(PropertyGroup):
    anim_name: StringProperty(
        name        = "Animation Name",
        description = "Name stored in the JSON meta block (used as default filename)",
        default     = "",
    )


# ─────────────────────────────────────────────
#  N-Panel UI
# ─────────────────────────────────────────────

class MCANIM_PT_MainPanel(Panel):
    bl_label       = "MC Anim Exporter"
    bl_idname      = "MCANIM_PT_main"
    bl_space_type  = "VIEW_3D"
    bl_region_type = "UI"
    bl_category    = "MC Anim"   # ← Tab name in the N-sidebar

    def draw(self, context):
        layout   = self.layout      # Blender 5.0+: layout lives on self, not context
        scene    = context.scene
        settings = scene.mcanim_settings

        # ── Settings ──────────────────────────────
        box = layout.box()
        box.label(text="Settings", icon="PREFERENCES")
        box.prop(settings, "anim_name", text="Name", icon="ACTION")
        box.label(text=f"FPS: {scene.render.fps}/{scene.render.fps_base}", icon="TIME")

        layout.separator()

        # ── Tagging ───────────────────────────────
        box = layout.box()
        box.label(text="Tag Objects", icon="BOOKMARKS")

        row = box.row(align=True)
        row.operator("mcanim.tag_selected",   icon="ADD",    text="Tag Selected")
        row.operator("mcanim.untag_selected", icon="REMOVE", text="Untag Selected")

        box.operator("mcanim.untag_all", icon="X", text="Clear All Tags")

        layout.separator()

        # ── Tagged object list ─────────────────────
        tagged = [o for o in scene.objects if o.get("anim_export", False)]
        box = layout.box()

        if tagged:
            box.label(text=f"Tagged ({len(tagged)})", icon="CHECKMARK")
            for obj in tagged:
                action = obj.animation_data.action if obj.animation_data else None
                action_name = action.name if action else "— no action —"
                row = box.row()
                row.label(text=obj.name, icon="OBJECT_DATA")
                row.label(text=action_name, icon="ACTION")
        else:
            box.label(text="No objects tagged", icon="INFO")

        layout.separator()

        # ── Export ────────────────────────────────
        layout.operator("mcanim.export", icon="EXPORT", text="Export Animation JSON")


# ─────────────────────────────────────────────
#  Registration
# ─────────────────────────────────────────────

CLASSES = [
    McAnimSettings,
    MCANIM_OT_Export,
    MCANIM_OT_TagSelected,
    MCANIM_OT_UntagSelected,
    MCANIM_OT_UntagAll,
    MCANIM_PT_MainPanel,
]


def register():
    for cls in CLASSES:
        bpy.utils.register_class(cls)
    bpy.types.Scene.mcanim_settings = bpy.props.PointerProperty(type=McAnimSettings)


def unregister():
    for cls in reversed(CLASSES):
        bpy.utils.unregister_class(cls)
    del bpy.types.Scene.mcanim_settings


if __name__ == "__main__":
    register()