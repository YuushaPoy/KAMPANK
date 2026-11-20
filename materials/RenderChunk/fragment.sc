$input v_clipPosition, v_worldPosition, v_color0, v_ditheringAndMaskTinting, v_fog, v_lightmapUV, v_texcoord0, v_worldPos, p

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/DitherUtil.dragonh>

uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 DitherParams;
uniform vec4 DitherParams2[3];
uniform vec4 ViewPositionAndTime;

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);
#if defined(SEASONS)
SAMPLER2D_AUTOREG(s_SeasonsTexture);
#endif


// ===== ACESFilm tonemap =====
vec3 ACESFilm(vec3 x) {
    float a = 3.0;
    float b = 0.001;
    float c = 3.1;
    float d = 0.48;
    float e = 0.1;
    return ((x * (a * x + b)) / (x * (c * x + d) + e));
}
// ========================

// ===== Tm tonemap (AlphaTest a = 3.0, Opaque dan Transparent a = 4.1) =====
vec3 Tm(vec3 x) {
#if defined(ALPHA_TEST)
    float a = 3.0;
#else
    float a = 4.1;
#endif
    float b = 0.01;
    float c = 2.6;
    float d = 0.56;
    float e = 0.4;
    vec3 hasil = (x * (a * x + b)) / (x * (c * x + d) + e);
    return hasil;
}

// ===== saturation =====
vec3 saturation(vec3 rgb, float adjustment) {
    vec3 W = vec3(0.2125, 0.7154, 0.0721);
    vec3 intensity = vec3_splat(dot(rgb, W));
    return mix(intensity, rgb, adjustment);
}

bool endFog(vec4 f) {
    return (f.r > f.g && f.b > f.g && f.r < 0.05 && f.b < 0.05 && f.g < 0.05);
}

// ===== hash, fresnel, genangan (dipakai blok hujan) =====
float hash12(vec2 q) {
    vec3 p3 = fract(vec3(q.xyx) * 0.13);
    p3 += dot(p3, p3.yzx + 3.333);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 fresnel(vec3 f0, float NdotV) {
    return f0 + (1.0 - f0) * pow(1.0 - NdotV, 5.0);
}

// bentuk genangan: kotak membulat acak per sel 4x4 blok (hanya ~30% sel yang berisi genangan)
float genangan(vec2 q) {
    vec2 g = q / 4.0;
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    vec2 c = (vec2(hash12(id + 7.3), hash12(id + 13.1)) - 0.5) * 0.2;
    vec2 hs = 0.15 + 0.17 * vec2(hash12(id + 3.7), hash12(id + 9.9));
    vec2 d = abs(f - c) - (hs - 0.1);
    float sd = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - 0.1;
    return step(0.7, hash12(id)) * (1.0 - smoothstep(-0.04, 0.04, sd));
}

void main() {
#if defined(DEPTH_ONLY) || defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY_ALPHA_TEST)
    #if defined(ALPHA_TEST)
    vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
    if (diffuse.a < 0.5) {
        discard;
    }
    #endif
    gl_FragColor = vec4_splat(0.0);
    return;
#else

    vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);

#if defined(ALPHA_TEST)
    bool ditherDiscard = false;
    #if defined(DITHERING)
    if (v_ditheringAndMaskTinting.x > 0.5) {
        ditherDiscard = evaluateDitherDiscard(v_clipPosition, v_worldPosition.xyz, ViewPositionAndTime.xyz, u_view, DitherParams, DitherParams2[2]);
    }
    #endif
    if (ditherDiscard || diffuse.a < 0.5) {
        discard;
    }
#endif

#if defined(SEASONS)
    diffuse.rgb *= mix(vec3_splat(1.0), texture2D(s_SeasonsTexture, v_color0.xy).rgb * 2.0, v_color0.b);
    diffuse.rgb *= v_color0.a;
    diffuse.a = 1.0;
#else
    if (v_ditheringAndMaskTinting.y > 0.5) {
        diffuse.rgb = mix(diffuse.rgb, diffuse.rgb * v_color0.rgb, diffuse.a) * v_color0.a;
        diffuse.a = 1.0;
    } else {
#if defined(OPAQUE)
        // ===== AO =====
        vec3 _aoMul = v_color0.rgb;
        if (abs(v_color0.g - v_color0.r) < 0.002) {
            float ao = mix(0.55, 0.8, smoothstep(0.3, 0.7, v_color0.g));
            float terbuka = smoothstep(0.55, 0.9, v_lightmapUV.y);
            terbuka = max(terbuka, step(FogAndDistanceControl.x, 0.00999) * (endFog(v_fog) ? 0.0 : 1.0));
            ao = mix(0.8, ao, terbuka);
            _aoMul = vec3_splat(ao);
        }
        diffuse.rgb *= _aoMul;
#else
        diffuse.rgb *= v_color0.rgb;
#endif
        #if !defined(TRANSPARENT)
            diffuse.a = 1.0;
        #else
            diffuse.a *= v_color0.a;
        #endif
    }
#endif

#if defined(TRANSPARENT) && defined(DITHERING)
    if (v_ditheringAndMaskTinting.x > 0.5) {
        if (evaluateDitherDiscard(v_clipPosition, v_worldPosition.xyz, ViewPositionAndTime.xyz, u_view, DitherParams, DitherParams2[2])) {
            diffuse.a = 0.0;
        }
    }
#endif

    diffuse.rgb *= texture2D(s_LightMapTexture, v_lightmapUV).rgb;
    diffuse.rgb = mix(diffuse.rgb, FogColor.rgb, v_fog.a);

#if !defined(SEASONS)
    vec4 _finalColor = diffuse;

    //var
    vec4 color = v_color0;
    vec4 kolor = v_color0;
    vec3 N = normalize(cross(dFdx(p.xyz), dFdy(p.xyz)));
    vec2 uv1 = v_lightmapUV;
    vec4 fclr = v_fog;
    vec4 fctl = FogAndDistanceControl;
    float Apa = 0.6;
    vec3 ks = v_clipPosition.xyz;
    vec3 wp = v_worldPos;
    float lenKs = length(-ks);
    float lenWp = length(wp);
    vec3 V = normalize(-wp);
    float NdotV = max(0.001, dot(N, V));

    //detect
    float goa = smoothstep(0.5, 0.0, uv1.y);
    float wk = texture2D(s_LightMapTexture, vec2(0.0, 1.0)).r;
    wk = clamp((wk - 0.5) * 2.0, 0.0, 1.0);
    float siangd = wk * uv1.y;
    float malamd = 1.0 - wk * uv1.y;
    float sored = 0.5 - abs(0.5 - wk) * uv1.y;
    vec2 rf = vec2(0.5 + 20.0 / FogAndDistanceControl.z, 1.0);
    vec2 rt = clamp((FogAndDistanceControl.xy - rf) / (vec2(0.23, 0.7) - rf), 0.0, 1.0);
    float rk = rt.x * rt.y;
    float hujand = rk * rk * (3.0 - 2.0 * rk);
    bool nether = (fclr.r > fclr.b && fclr.r < 0.5 && fclr.b < 0.2);
    bool akhir = endFog(fclr);
#if defined(TRANSPARENT)
    bool udw = (fctl.x == 0.0) && !akhir;
#else
    bool udw = (fctl.x < 0.01) && !akhir;
#endif
    float isnether = step(fclr.b, fclr.r) * step(fclr.r, 0.4999) * step(fclr.b, 0.1999);
    float isudw = step(fctl.x, 0.00999) * (akhir ? 0.0 : 1.0);
    float isakhir = akhir ? 1.0 : 0.0;

#if defined(OPAQUE)
    float dudan = smoothstep(0.55, 0.1, fctl.r) * uv1.y;

    //dirlight
    float dir = max(N.x, -N.z);
    _finalColor.xyz *= mix(vec3_splat(0.75), vec3_splat(0.65), dir * (1.0 - pow(v_lightmapUV.x * 0.9, 4.7)) * (1.0 - goa) * (1.0 - hujand) * (1.0 - malamd));

    //coloring, saturation and tonemapping
    if (!akhir) {
        _finalColor.rgb *= vec3_splat(1.7);
        _finalColor.rgb = saturation(_finalColor.rgb, 1.2);
        if (!(udw)) {
            _finalColor.xyz *= ACESFilm(_finalColor.xyz);
        }
        _finalColor.xyz = Tm(_finalColor.xyz);
        if (color.a == 0.0) {
            _finalColor.rgb *= 1.1;
        }
    }

    //shadow
    float shadowRaw = smoothstep(1.0, 0.95, uv1.y);
    float shadow = shadowRaw;
    float nolight = (1.0 - uv1.x);
    shadow *= nolight;
    shadow *= (1.0-hujand)*(1.0-hujand)*(1.0-hujand)*(1.0-hujand)*(1.0-hujand)*(1.0-malamd)*(1.0-malamd)*(1.0-malamd)*(1.0-malamd)*(1.0-malamd);
    shadow = mix(0.0, 0.65, shadow);
    _finalColor.rgb = mix(_finalColor.rgb, vec3(0.0, 0.0, 0.12), shadow);

    //hujan
    if (!(udw && nether && akhir)) {
        _finalColor.rgb += vec3(-0.2, -0.1, -0.05) * dudan * (1.0 - malamd);

        //genangan (hanya permukaan atas yang terbuka ke langit)
        if (hujand > 0.0) {
            float langitTerbuka = smoothstep(0.88, 0.94, uv1.y);
            float gn = genangan(p.xz) * langitTerbuka * hujand * step(0.5, N.y);
            vec3 gnBasah = mix(_finalColor.rgb, vec3_splat(dot(_finalColor.rgb, vec3_splat(0.333))), 0.4) * 0.7;
            vec3 gnKilap = mix(gnBasah, FogColor.rgb * 1.2 + 0.1, fresnel(vec3_splat(0.15), NdotV));
            _finalColor.rgb = mix(_finalColor.rgb, gnKilap, gn);
        }
    }

    //light
    if (!(akhir)) {
        float jitB = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        float Lb = uv1.x + (jitB - 0.5) * 0.0625;
        float core = smoothstep(0.72, 0.94, Lb);
        core *= core;
        float night = 1.0 - siangd * 0.8;
        float flick = 0.9 + 0.1 * sin(ViewPositionAndTime.w * 3.0 + p.x * 0.7 + p.z * 0.7);
        float glow = core * night * flick * (1.0 - saturate(lenWp / 80.0));
        _finalColor.rgb += vec3(1.0, 0.55, 0.2) * glow * 0.6 * (1.0 - saturate(_finalColor.rgb));
    }

#elif defined(ALPHA_TEST)
    float dirAlpha = (max(min(N.r - N.b, 1.0), 0.0) + max(min(N.b - N.r, 1.0), 0.0));

    //coloring, saturation and tonemap
    if (kolor.g > kolor.r) {
        _finalColor.rgb *= max(1.1, 1.0 - dirAlpha);
    }
    if (!akhir) {
        _finalColor.rgb *= vec3_splat(1.7);
        _finalColor.rgb = saturation(_finalColor.rgb, 1.2);
        _finalColor.xyz *= ACESFilm(_finalColor.xyz);
        _finalColor.xyz = Tm(_finalColor.xyz);
    }

    //dirlight
    if (!(kolor.g > kolor.r)) {
        float dir = max(N.x, -N.z);
        _finalColor.xyz *= mix(vec3_splat(0.95), vec3_splat(0.558), dir * (1.0 - v_lightmapUV.x) * (1.0 - dirAlpha));
    }
    if (kolor.g > kolor.r) {
        _finalColor.rgb = mix(_finalColor.rgb, _finalColor.rgb * 0.55, dirAlpha * (1.0 - v_lightmapUV.x));
    }

    //shadow (variabel pendukung; belum dipakai untuk mewarnai)
    float shadowRaw = smoothstep(1.0, 0.95, uv1.y);
    float shadow = shadowRaw;
    shadow *= (1.0 - uv1.x);
    shadow *= (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand);
    shadow = mix(0.0, 0.65, shadow);

#elif defined(TRANSPARENT)
    bool banyu = (kolor.b > kolor.g || kolor.b > kolor.r);

    //coloring, saturation and tonemap (bukan air)
    if (!(banyu)) {
        if (!akhir) {
            _finalColor.rgb *= vec3_splat(1.7);
            _finalColor.rgb = saturation(_finalColor.rgb, 1.2);
            if (!(udw)) {
                _finalColor.xyz *= ACESFilm(_finalColor.xyz);
            }
            _finalColor.xyz = Tm(_finalColor.xyz);
        }

        //shadow (variabel pendukung; bagian shadow di air menyusul bersama air)
        float shadowRaw = smoothstep(1.0, 0.95, uv1.y);
        float shadow = shadowRaw;
        shadow *= (1.0 - uv1.x);
        shadow *= (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand);
        shadow = mix(0.0, 0.65, shadow);
    }
#endif

    //fog
#if defined(OPAQUE)
    if (!(akhir)) {
        vec4 fgst = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(lenKs / 100.0)), (1.0 - hujand));
        _finalColor = fgst;
        _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(lenKs / 20.0)), dudan);
    }
#elif defined(ALPHA_TEST)
    _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(length(-ks) / 100.0)), (1.0 - hujand));
    _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(length(-ks) / 20.0)), hujand);
#elif defined(TRANSPARENT)
    _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(length(-wp) / 100.0)), (1.0 - hujand));
#endif

    diffuse = _finalColor;
#endif

    gl_FragColor = diffuse;
#endif
}
