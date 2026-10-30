# Texture guidelines for vanilla-style quality

- Size: 16x16 for blocks and items (32x32 or more only if the whole mod uses that resolution consistently).
  Entity textures follow the model UV map, armor layers are 64x32, GUI screens usually 176x166 inside 256x256.
- Light comes from the top-left: highlights on the top/left edges, shadows bottom/right.
- Use a limited palette (4-6 shades per material) with hue shifting (shadows slightly cooler/more saturated,
  highlights warmer) instead of pure black/white shading.
- Items: 1px dark outline on the transparent background, centered, diagonal tools from bottom-left handle to
  top-right head like vanilla.
- No anti-aliasing, gradients or JPEG noise; always transparent background for items.
- Variants of a material (ingot, nugget, block, ore, tools, armor) should share one palette. A good workflow:
  texture_edit TRANSFORM with colorize/hue_shift on an existing texture, then PREVIEW and refine.
- Animated textures: vertical strip of square frames + `.png.mcmeta` (texture_import animationFrameTime or
  texture_edit ANIMATE).
- Ore textures: base stone (or deepslate) texture + overlay of mineral pixels.
