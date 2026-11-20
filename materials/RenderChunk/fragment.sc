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
    }
#endif

    diffuse = _finalColor;
#endif

    gl_FragColor = diffuse;
#endif
}
