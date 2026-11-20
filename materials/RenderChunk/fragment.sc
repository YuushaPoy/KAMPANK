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


// kode tambahan aktif di semua pass kecuali SEASONS, kecuali AlphaTest (daun SEASONS ikut diwarnai)
#if !defined(SEASONS) || defined(ALPHA_TEST)
    #define KODE_EXTRA 1
#endif

// ===== warna terrain dimensi End =====
#define END_BRIGHT     4.0
#define END_SAT        0.7
#define END_LM_TINT    0.35
#define END_SHOULDER   0.7
#define END_BLOCKLIGHT 0.55
#define END_LIGHT_COL  vec3(1.1, 0.9, 1.55)
#define END_LIGHT_AMT  0.85
#define END_DIM        0.55

// ===== bayangan awan (lantai dan sisi block) =====
#define BAYANG_AWAN 1
#define AWAN_SPEED 0.04
#define BAYANG_AWAN_TINGGI 40.0
#define BAYANG_AWAN_KUAT 0.38
#define BAYANG_AWAN_DITHER 0.02

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

#if defined(KODE_EXTRA)
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

// ===== air: saturasi sederhana dan noise ombak =====
vec3 satuR(vec3 v, float val) {
    vec3 sat = vec3_splat(dot(v, vec3_splat(1.0 / 3.0)));
    return mix(sat, v, val);
}

float noiseWave(vec2 x) {
    x.y -= ViewPositionAndTime.w * 0.02;
    vec2 i = floor(x);
    vec2 f = fract(x);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

#if defined(ALPHA_TEST) || defined(OPAQUE)
// ===== bayangan awan (rumus sama dengan shader langit) =====
float hashAwan(vec2 q) {
    return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453);
}

float awanBayang(vec2 awBase) {
    float awDh = hashAwan(awBase * 999.0);
    return step(0.88, hashAwan(floor(awBase * 1.33 + awDh * BAYANG_AWAN_DITHER + ViewPositionAndTime.w * AWAN_SPEED)));
}

// ===== caustic (voronoi) =====
// versi GLSL asli memakai "v.xyw *= mat3(...)"; ditulis manual supaya aman di semua platform
vec3 vmatCaustic(vec3 v) {
    return vec3(-v.y + 2.0 * v.z, 3.0 * v.x - 2.0 * v.y + v.z, v.x + 2.0 * v.y + 2.0 * v.z);
}

float vorolit(vec2 uv) {
    float tm = ViewPositionAndTime.w;
    uv -= tm * 0.02;
    uv *= 3.0;
    vec3 q = vec3(uv * (sin(tm * 0.2) + 1.5) * 0.5, tm * 0.3);
    q = vmatCaustic(q) * 0.5;
    float l5 = length(vec3_splat(0.5) - fract(q));
    q = vmatCaustic(q) * 0.4;
    float l4 = length(vec3_splat(0.5) - fract(q));
    q = vmatCaustic(q) * 0.3;
    float l3 = length(vec3_splat(0.5) - fract(q));
    return pow(min(min(l5, l4), l3), 3.0) * 4.0;
}

// arah sinar matahari bawah air
#define RAY_D  vec3(0.324617, -0.927478, 0.185496)
#define RAY_T1 vec3(-0.943858, -0.330350, 0.0)
#define RAY_T2 vec3(0.061279, -0.175082, -0.982645)

float causticRay(vec3 pp, vec3 nn) {
    vec2 q = vec2(dot(pp, RAY_T1), dot(pp, RAY_T2));
    float c = smoothstep(0.2, 1.0, vorolit(q * 0.35));
    c *= mix(0.6, 1.0, saturate(dot(nn, -RAY_D)));
    return c;
}

// fc = gl_FragCoord.xy, dikirim dari main (di HLSL gl_FragCoord tidak terlihat di dalam fungsi lain)
float godRayFog(vec3 pp, vec3 ww, vec2 fc) {
    float jit = fract(dot(fc, vec2(0.06711056, 0.00583715)) * 52.9829189);
    float acc = 0.0;
    for (int i = 0; i < 4; i++) {
        float t = (float(i) + jit) * 0.25;
        vec3 sp = pp - ww * (1.0 - t);
        vec2 q = vec2(dot(sp, RAY_T1), dot(sp, RAY_T2));
        acc += smoothstep(0.45, 0.8, noiseWave(q * 0.9));
    }
    return acc * 0.25;
}
#endif // ALPHA_TEST || OPAQUE
#endif // KODE_EXTRA

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

#if defined(ALPHA_TEST) || (defined(OPAQUE) && !defined(SEASONS))
    // lightmap AlphaTest dan Opaque: di dimensi End cahaya block diredam dan diberi warna ungu
    bool _akhirE = endFog(v_fog);
    vec2 _lmUV = v_lightmapUV;
    if (_akhirE) {
        _lmUV.x *= END_BLOCKLIGHT;
    }
    vec3 _lm = texture2D(s_LightMapTexture, _lmUV).rgb;
    if (_akhirE) {
        float _lmL = dot(_lm, vec3(0.2125, 0.7154, 0.0721));
        _lm = mix(vec3_splat(_lmL), _lm, END_LM_TINT);
        _lm = mix(_lm, _lmL * END_LIGHT_COL, saturate(v_lightmapUV.x) * END_LIGHT_AMT);
    }
    diffuse.rgb *= _lm;
#else
    diffuse.rgb *= texture2D(s_LightMapTexture, v_lightmapUV).rgb;
#endif
    diffuse.rgb = mix(diffuse.rgb, FogColor.rgb, v_fog.a);

#if defined(KODE_EXTRA)
    vec4 _finalColor = diffuse;

    //var
#if defined(SEASONS)
    // di SEASONS v_color0 berisi data musim (bukan warna vertex); AlphaTest SEASONS = daun (hijau)
    vec4 color = vec4(0.0, 1.0, 0.0, 1.0);
    vec4 kolor = color;
#else
    vec4 color = v_color0;
    vec4 kolor = v_color0;
#endif
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

    float endK = (fctl.x < 0.01) ? END_DIM : 1.0;

    //underwater
    if (udw && !(nether && akhir)) {
        _finalColor.rgb *= vec3(0.3, 1.1, 0.85);
        _finalColor = mix(_finalColor, v_fog * sqrt(vec4(0.8, 1.5, 1.4, 1.0)), Apa * saturate(lenKs / 50.0));
        _finalColor.rgb += _finalColor.rgb * (vec3(0.0, 0.7, 1.0) * 1.15) * pow(v_lightmapUV.x * 0.5, 4.7);
        _finalColor.xyz *= ACESFilm(_finalColor.xyz);

        //caustic
        float skyMax = 0.9375;
        float topBand = smoothstep(skyMax - 0.01, skyMax - 0.001, uv1.y);
        float belowEye = 1.0 - smoothstep(0.7, 1.2, wp.y);
        float inWater = mix(1.0, belowEye, topBand);
        float lit = smoothstep(skyMax - 0.24, skyMax - 0.02, uv1.y);
        float cmask = inWater * lit * (1.0 - saturate(lenKs / 20.0));
        if (cmask > 0.001) {
            _finalColor.rgb += vec3(0.6, 0.9, 1.0) * causticRay(p, N) * 0.35 * cmask;
        }

        //god rays
        if (wk > 0.05) {
            float fog2 = smoothstep(1.0, 2.0, lenWp);
            if (fog2 > 0.01) {
                float dens = godRayFog(p, wp, gl_FragCoord.xy);
                float bukanBawah = smoothstep(-0.7, -0.2, N.y);
                float a = dens * fog2 * wk * uv1.y * 0.5 * bukanBawah;
                _finalColor.rgb = mix(_finalColor.rgb, vec3(0.5, 1.05, 1.2), saturate(a));
            }
        }
    }

    //nether
    if (nether) {
        _finalColor.rgb *= vec3(1.5, 0.9, 0.0) * (1.0 - sored);
        _finalColor.rgb += vec3_splat(0.2);
        _finalColor.rgb = Tm(_finalColor.rgb) * 0.9;
        vec3 fogn = mix(_finalColor.rgb, fclr.rgb * vec3(1.5, 1.0, 0.0), clamp(lenKs / fctl.z * 1.5, 0.1, 0.2));
        _finalColor.rgb = fogn;
    }

    //end
    if (akhir) {
        vec3 ec = _finalColor.rgb * END_BRIGHT * endK;
        ec = saturation(ec, END_SAT);
        vec3 eo = max(ec - vec3_splat(END_SHOULDER), vec3_splat(0.0));
        float ek = 1.0 - END_SHOULDER;
        ec = min(ec, vec3_splat(END_SHOULDER)) + ek * (vec3_splat(1.0) - exp(-eo / ek));
        _finalColor.rgb = ec;

        float drl = max(max(-N.x, -N.z), -N.y);
        _finalColor.xyz *= mix(vec3_splat(0.95), vec3_splat(0.5), drl * (1.0 - v_lightmapUV.x));
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

    //shadow
    float shadowRaw = smoothstep(1.0, 0.95, uv1.y);
    float shadow = shadowRaw;
    shadow *= (1.0 - uv1.x);
    shadow *= (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand);
    shadow = mix(0.0, 0.65, shadow);
    float endK = (fctl.x < 0.01) ? END_DIM : 1.0;

    //bayangan awan (lantai dan sisi block, bagian bawah tidak)
#if BAYANG_AWAN
    if (N.y > -0.5) {
        // hilang: malam, hujan, bawah air/nether/end, dan di bawah shadow block
        float bAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadowRaw);
        if (bAktif > 0.001) {
            float bDy = BAYANG_AWAN_TINGGI / sqrt(dot(wp.xz, wp.xz) + BAYANG_AWAN_TINGGI * BAYANG_AWAN_TINGGI);
            float bMask = awanBayang(wp.xz / BAYANG_AWAN_TINGGI) * smoothstep(0.0, 0.12, bDy);
            _finalColor.rgb = mix(_finalColor.rgb, vec3(0.0, 0.0, 0.12), BAYANG_AWAN_KUAT * bMask * bAktif);
        }
    }
#endif

    //end
    if (akhir) {
        vec3 ec = _finalColor.rgb * END_BRIGHT * endK;
        ec = saturation(ec, END_SAT);
        vec3 eo = max(ec - vec3_splat(END_SHOULDER), vec3_splat(0.0));
        float ek = 1.0 - END_SHOULDER;
        ec = min(ec, vec3_splat(END_SHOULDER)) + ek * (vec3_splat(1.0) - exp(-eo / ek));
        _finalColor.rgb = ec;

        float drl = max(-N.x, -N.z);
        _finalColor.xyz *= mix(vec3_splat(0.85), vec3_splat(0.65), drl * (1.0 - pow(v_lightmapUV.x * 0.9, 4.7)));
    }

    //underwater
    if (udw) {
        _finalColor.rgb *= vec3(0.3, 1.1, 0.85);
        _finalColor = mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 1.5, 1.0)), Apa * saturate(lenKs / 50.0));
        _finalColor.rgb += _finalColor.rgb * (vec3(0.0, 0.7, 1.0) * 1.15) * pow(v_lightmapUV.x * 0.9, 4.7);

        //caustic
        float skyMax = 0.9375;
        float topBand = smoothstep(skyMax - 0.01, skyMax - 0.001, uv1.y);
        float belowEye = 1.0 - smoothstep(0.7, 1.2, wp.y);
        float inWater = mix(1.0, belowEye, topBand);
        float lit = smoothstep(skyMax - 0.24, skyMax - 0.02, uv1.y);
        float cmask = inWater * lit * (1.0 - saturate(lenKs / 20.0));
        if (cmask > 0.001) {
            _finalColor.rgb += vec3(0.6, 0.9, 1.0) * causticRay(p, N) * 0.35 * cmask;
        }

        //god rays
        if (wk > 0.05) {
            float fog2 = smoothstep(1.0, 2.0, lenWp);
            if (fog2 > 0.01) {
                float dens = godRayFog(p, wp, gl_FragCoord.xy);
                float bukanBawah = smoothstep(-0.7, -0.2, N.y);
                float a = dens * fog2 * wk * uv1.y * 0.5 * bukanBawah;
                _finalColor.rgb = mix(_finalColor.rgb, vec3(0.5, 1.05, 1.2), saturate(a));
            }
        }
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

        //shadow (variabel pendukung; shadow khusus air ada di blok //air -> shadowAir)
        float shadowRaw = smoothstep(1.0, 0.95, uv1.y);
        float shadow = shadowRaw;
        shadow *= (1.0 - uv1.x);
        shadow *= (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand);
        shadow = mix(0.0, 0.65, shadow);
    }

    //air
    if (banyu) {
        vec3 f0 = vec3_splat(0.05);
        vec3 pot = p * 0.2;
        float tm = ViewPositionAndTime.w;

        //gambar ombak (dari noise)
        float wave = sin(noiseWave(vec2(tm * 0.7 + pot.x + pot.x + pot.z * 3.0, tm * 1.2 + pot.z + pot.x + pot.z * 1.0 + pot.x * 2.0)));

        //warna air
        _finalColor = vec4(0.3, 0.5, 1.0, 1.0) * 0.7 * siangd * (1.0 - hujand);
        _finalColor += vec4(0.3, 0.5, 0.7, 1.2) * 0.5 * malamd * (1.0 - hujand);
        _finalColor.rgb += vec3_splat(0.5) * 0.3 * hujand;
        _finalColor.rgb += vec3(0.1, 0.5, 1.0) * sored * (1.0 - hujand);
        _finalColor.rgb = satuR(_finalColor.rgb, 1.2) * siangd * (1.0 - goa);
        _finalColor.rgb += vec3(0.3, 0.5, 0.7) * goa;

        //gradasi malam (warna sama dengan langit malam: abu gelap -> biru di sudut pandang miring/horizon)
        float gradAir = clamp((1.0 - NdotV) * 1.6, 0.1, 1.0);
        vec3 airMalam = mix(vec3_splat(0.1), vec3(0.1, 0.5, 1.0), gradAir);
        _finalColor.rgb += airMalam * malamd * (1.0 - hujand) * (1.0 - goa) * 0.6;

        //transparansi air
        _finalColor.a = lenWp / 15.0;
        _finalColor.a *= 1.5
        }
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