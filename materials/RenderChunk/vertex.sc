$input a_color0, a_position, a_texcoord0, a_texcoord1
#ifdef INSTANCING
    $input i_data1, i_data2, i_data3
#endif

$output v_clipPosition, v_color0, v_ditheringAndMaskTinting, v_fog, v_lightmapUV, v_texcoord0, v_worldPos, v_worldPosition, p

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/TAAUtil.dragonh>

uniform vec4 RenderChunkFogAlpha;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 FogColor;
uniform vec4 MeshContext;

// intensitas fog bawaan AlphaTest dikali ini (fog tambahan ada di fragment, blok //fog)
#define FOG_AMOUNT 0.3

//bawah air (bukan nether, bukan End)
bool underwater() {
    vec4 f = FogColor;
    bool nether = (f.r > f.b && f.r < 0.5 && f.b < 0.2);
    float ungu = smoothstep(0.0, 0.012, min(f.r, f.b) - f.g);
    float gelap = 1.0 - smoothstep(0.05, 0.14, max(f.r, max(f.g, f.b)));
    bool akhir = ungu * gelap > 0.01;
    bool biru = (f.b > f.r || f.g > f.r);
    return FogAndDistanceControl.x < 0.01 && !nether && !akhir && biru;
}

//gerakan vertex (offset dalam ruang dunia)
vec3 gerak(vec3 lpos, vec4 col, float sky) {
    float t = ViewPositionAndTime.w;
    float ph = (lpos.x + lpos.y + lpos.z) * 1.1781;
    vec3 frp = fract(lpos);
    vec3 o = vec3_splat(0.0);

#if defined(ALPHA_TEST) || defined(DEPTH_ONLY_ALPHA_TEST)
    //daun dan rumput
    bool tinted = (col.r != col.g && col.g != col.b && frp.y != 0.015625);
    bool pucuk = (frp.y == 0.9375 && (frp.x == 0.0 || frp.z == 0.0));
    if (col.a == 0.0 || tinted || pucuk) {
        o.x += sin(t * 2.0 + ph) * 0.03;
    }
#endif

#if defined(TRANSPARENT)
    //air
    if (col.b > col.r) {
        o.y += sin(t * 4.0 + (lpos.x + lpos.z) * 1.9635 + lpos.y * 0.3927) * sin(lpos.z * 1.1781) * 0.05 * sky * frp.y;
    }
#endif

    //underwater
    if (underwater()) {
        o.x += sin(t * 2.0 + ph) * 0.03;
    }
    return o;
}

void main() {
#ifdef INSTANCING
    mat4 model = mtxFromCols(i_data1, i_data2, i_data3, vec4(0.0, 0.0, 0.0, 1.0));
    vec3 worldPos = instMul(model, vec4(a_position, 1.0)).xyz;
#else
    vec3 worldPos = mul(u_model[0], vec4(a_position, 1.0)).xyz;
#endif

    vec4 color;
#ifdef RENDER_AS_BILLBOARDS
    worldPos += vec3_splat(0.5);
    vec3 viewDir = normalize(worldPos - ViewPositionAndTime.xyz);
    vec3 boardPlane = normalize(cross(vec3(0.0, 1.0, 0.0), viewDir));
    worldPos -= (cross(viewDir, boardPlane) * (a_color0.z - 0.5)) + (boardPlane * (a_color0.x - 0.5));
    color = vec4_splat(1.0);
#else
    color = a_color0;
#endif

    vec4 fogAndDist = FogAndDistanceControl;
    if (MeshContext.x > 0.5) {
        fogAndDist = vec4(0.99, 1.0, 100000.0, 100000.0);
    }

    uvec2 uv0_16 = uvec2(round(a_texcoord0 * 65535.0));
    vec2 uv0 = vec2(float((uv0_16.x & 32767u) << uint(1)), float((uv0_16.y & 32767u) << uint(1))) * 1.525902189314365386962890625e-05;
    uv0.x += 3.0517578125e-05 * ((2.0 * float((uv0_16.x & 32768u) >> uint(15))) - 1.0);
    uv0.y += 3.0517578125e-05 * ((2.0 * float((uv0_16.y & 32768u) >> uint(15))) - 1.0);

    uvec2 uv1_16 = uvec2(round(a_texcoord1 * 65535.0));
    float lightmapX = float((uv1_16.y >> 4u) & 15u) * 0.06666667;
    float lightmapY = float(uv1_16.y & 15u) * 0.06666667;
    vec2 lightmapUV = vec2(lightmapX, lightmapY);

    float ditherX = float((uv1_16.x & 256u) != 0u ? 1.0 : 0.0);
    float ditherY = float((uv1_16.y & 256u) != 0u ? 1.0 : 0.0);
    vec2 ditheringAndMaskTinting = vec2(ditherX, ditherY);

    //gerak
    vec3 gerakOff = vec3_splat(0.0);
#if !defined(INSTANCING) && !defined(RENDER_AS_BILLBOARDS)
    gerakOff = gerak(a_position, a_color0, lightmapUV.y);
#endif
    vec4 position = jitterVertexPosition(worldPos + gerakOff);

    float camDist = length(ViewPositionAndTime.xyz - worldPos);
    float fogIntensity = clamp((((camDist / fogAndDist.z) + RenderChunkFogAlpha.x) - fogAndDist.x) / (fogAndDist.y - fogAndDist.x), 0.0, 1.0);
#if !defined(SEASONS)
  #if defined(OPAQUE)
    fogIntensity = 0.0;
  #elif defined(ALPHA_TEST)
    fogIntensity *= FOG_AMOUNT;
  #endif
#endif

    vec4 fog = vec4(FogColor.rgb, fogIntensity);

#if defined(TRANSPARENT)
    if (a_color0.a < 0.95) {
        color.a = mix(a_color0.a, 1.0, clamp(camDist / FogAndDistanceControl.w, 0.0, 1.0));
    }
#endif

    v_clipPosition = position;
    v_worldPosition = vec4(worldPos, 1.0);
    v_color0 = color;
    v_ditheringAndMaskTinting = ditheringAndMaskTinting;
    v_fog = fog;
    v_lightmapUV = lightmapUV;
    v_texcoord0 = uv0;
    v_worldPos = worldPos;
    p = a_position;
    gl_Position = position;
}
