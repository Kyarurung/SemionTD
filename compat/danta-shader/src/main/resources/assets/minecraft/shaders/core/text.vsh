#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

uniform sampler2D Sampler0;
#include <minecraft:globals.glsl>
#include <minecraft:hud.glsl>
layout(location = 4) flat out int effectId;
layout(location = 5) flat out int frames;
layout(location = 6) flat out int fps;
layout(location = 7) flat out float frameheight;
const vec2[4] corners = vec2[4](vec2(0), vec2(0, 1), vec2(1), vec2(1, 0));

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif
    texCoord0 = UV0;
    vec4 col = round(texture(Sampler0, UV0) * 255.0);
    effectId = 0;
    frames = fps = 0;
    frameheight = 0.0;
    if (col.a == 251.0) {
        vec2 coord = corners[gl_VertexIndex % 4];
        effectId = int(col.b);
        gl_Position.xy = (coord * 2.0 - 1.0) * vec2(1.0, -1.0);
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
        gl_Position.zw = vec2(0.0, 1.0);
#else
        gl_Position.zw = vec2(-1.0, 1.0);
#endif
        vertexColor = Color;
        texCoord0 = UV0 - coord * 64.0 / 256.0;
    } else if (col.a == 253.0) {
        effectId = int(col.b);
        vertexColor = Color;
    }
    if (col.a == 252.0 && Position.z == 0.0) {
        frames = int(col.r);
        fps = int(col.g);
        frameheight = col.b;
    }
    if (make_hud()) {
        vertexColor = Color;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
#endif
        return;
    }
}
