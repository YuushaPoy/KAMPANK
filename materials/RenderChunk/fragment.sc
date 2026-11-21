$input v_clipPosition, v_worldPosition, v_color0, v_ditheringAndMaskTinting, v_fog, v_lightmapUV, v_texcoord0, v_worldPos, p

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/DitherUtil.dragonh>

uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;

// ===== fog bawah air (tebal, menutup dasar laut) =====
// UDW_FOG_MAX   : pekat maksimum di kejauhan (1.0 = menutup penuh)
// UDW_FOG_START : jarak mulai terasa
// UDW_FOG_END   : jarak yang sudah mencapai pekat maksimum
#define UDW_FOG_MAX   1.0
#define UDW_FOG_START 8.0
#define UDW_FOG_END   45.0
float udwFog(float d) {
    return UDW_FOG_MAX * smoothstep(UDW_FOG_START, UDW_FOG_END, d);
}
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

// ===== metalic (alpha texture 250): hanya memantulkan warna langit, tanpa awan =====
#define SISI_HALUS 0.15   // kehalusan peralihan warna atas/bawah di dinding sekitar tinggi mata

// ===== gradasi biru air saat malam =====
#define AIR_GRAD_MULAI 20.0   // jarak (blok) mulai bergradasi ke biru
#define AIR_GRAD_PENUH 100.0  // jarak (blok) biru penuh (makin besar = biru makin jauh)

// ===== glow (dibaca dari alpha texture: 252 = glow penuh, 253 = glow redup) =====
#define GLOW_INTENSITY 0.85       // kekuatan glow (0.0 = mati)
#define GLOW_SIANG 0.35           // glow siang hari sebagai fraksi dari glow di tempat gelap
#define GLOW_DENYUT 0.08          // denyut halus (0.0 = tidak berdenyut)
#define GLOW_LANGIT_MIN 0.55      // cahaya langit (lightmap.y): glow penuh di bawah MIN, hilang di atas MAX
#define GLOW_LANGIT_MAX 0.90
#define GLOW_MALAM_TERBUKA 1.0    // 1.0 = malam hari glow tetap menyala di langit terbuka (0.0 = perilaku lama)

// ===== bayangan awan (lantai dan sisi block) =====
#define BAYANG_AWAN 1
#define AWAN_SPEED 0.04
#define BAYANG_AWAN_TINGGI 40.0
#define BAYANG_AWAN_KUAT 0.38
#define BAYANG_AWAN_DITHER 0.02

// ===== refleksi awan di air: harus SAMA dengan shader langit =====
// kalau pola awan terbalik, coba AWAN_SX = -1.0 dan/atau AWAN_SZ = -1.0
#define AWAN_SX 1.0
#define AWAN_SZ 1.0
#define AWAN_AIR_KUAT 0.3     // kekuatan awan di pantulan air
#define AWAN_AIR_TINGGI 10.0  // tinggi awan tetap untuk pantulan: makin KECIL = awan makin kecil (ukuran sel ~ tinggi / 1.4 blok)

// ===== god rays bawah air =====
#define GODRAY_POWER 0.8        // kekuatan berkas cahaya (0.0 = mati)
#define GODRAY_DEKAT 5.0        // jarak (blok) sampai berkas penuh dari dekat kamera
#define GODRAY_JAUH_MIN 24.0    // mulai memudar di kejauhan
#define GODRAY_JAUH_MAX 48.0    // hilang total di kejauhan
#define GODRAY_GESER 0.10       // kecepatan berkas bergeser
#define GODRAY_PERIODE 8.0      // pola berulang tiap 8 blok (membagi 16, jadi menyambung mulus di batas chunk)
#define GODRAY_MIRING 0.5       // kemiringan berkas; 16 x 0.5 = 8 = 1 periode, jadi menyambung juga antar chunk vertikal
#define GODRAY_KEDIP 0.30       // kedalaman kedipan (0.0 = tidak berkedip)

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

// ===== dither Bayer 8x8 (ordered dither), hasil 0..1 =====
// fc = gl_FragCoord.xy (dikirim dari main karena di HLSL gl_FragCoord tidak terlihat di dalam fungsi lain)
float bayer2(vec2 a) {
    a = floor(a);
    return fract(a.x * 0.5 + a.y * a.y * 0.75);
}

float bayer4(vec2 a) {
    return bayer2(0.5 * a) * 0.25 + bayer2(a);
}

float bayer8(vec2 a) {
    return bayer4(0.5 * a) * 0.25 + bayer2(a);
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

// ===== glow =====
// kode alpha texture -> kekuatan glow
float oreGlowCode(float a) {
    int c = int(a * 255.0 + 0.5);
    return (c == 252) ? 1.0 : ((c == 253) ? 0.4 : 0.0);
}

// faktor kecerahan glow: jarak, naungan langit, siang/malam, denyut
// skyL = lightmap.y, wkS = kecerahan langit (wk), malamS = malamd, lenK = jarak ke kamera
float glowFactor(float lenK, float skyL, float wkS, float malamS) {
    float fade = 1.0 - smoothstep(0.6, 1.0, lenK / FogAndDistanceControl.z);
    // aturan lama: makin terbuka ke langit, glow makin hilang
    float naungan = 1.0 - smoothstep(GLOW_LANGIT_MIN, GLOW_LANGIT_MAX, skyL);
    // aturan baru: saat malam, langit terbuka tidak lagi meredam glow
    float malamTerbuka = (1.0 - smoothstep(0.1, 0.4, wkS)) * GLOW_MALAM_TERBUKA;
    naungan = max(naungan, malamTerbuka);
    float terang = mix(GLOW_SIANG, 1.0, malamS) * naungan;
    float pulse = 1.0 + GLOW_DENYUT * sin(ViewPositionAndTime.w * 1.5);
    return terang * fade * pulse;
}

// hash awan: rumus sama dengan shader langit (sin), dipakai bayangan awan dan refleksi awan di air
float hashAwan(vec2 q) {
    return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453);
}

#if defined(ALPHA_TEST) || defined(OPAQUE)
// ===== bayangan awan (rumus sama dengan shader langit) =====

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

// noise 2D yang berulang tiap 'per' sel, supaya pola menyambung mulus di batas chunk
float noiseTile(vec2 x, float per) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    vec2 i0 = i - per * floor(i / per);
    vec2 i1 = i + vec2(1.0, 1.0);
    i1 = i1 - per * floor(i1 / per);
    float a = hash12(i0);
    float b = hash12(vec2(i1.x, i0.y));
    float c = hash12(vec2(i0.x, i1.y));
    float d = hash12(i1);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

// pp = p (posisi lokal chunk piksel ini), ww = wp (vektor kamera -> piksel, hanya untuk arah dan panjang garis pandang),
// fc = gl_FragCoord.xy (dikirim dari main karena di HLSL tidak terlihat di dalam fungsi lain)
// Pola berjangkar pada p, bukan pada posisi kamera, jadi tidak ikut bergerak bersama pemain.
// Pola berulang tiap GODRAY_PERIODE blok (pembagi 16), jadi tidak terpotong di batas chunk.
float godRayFog(vec3 pp, vec3 ww, vec2 fc) {
    float tm = ViewPositionAndTime.w;
    float jit = bayer8(fc);
    float acc = 0.0;
    for (int i = 0; i < 6; i++) {
        float t = (float(i) + jit) / 6.0;
        vec3 sp = pp - ww * (1.0 - t);
        // berkas miring + bergeser pelan menyamping seiring waktu
        vec2 q = vec2(sp.x + sp.y * GODRAY_MIRING + tm * GODRAY_GESER, sp.z + tm * GODRAY_GESER * 0.4);
        float n = noiseTile(q, GODRAY_PERIODE);
        // tiap berkas berkedip dengan fase sendiri
        float kedip = 1.0 - GODRAY_KEDIP + GODRAY_KEDIP * (0.5 + 0.5 * sin(tm * 1.4 + n * 12.0));
        acc += smoothstep(0.40, 0.75, n) * kedip;
    }
    return acc / 6.0;
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
    // texture LOD 0: alpha dipakai sebagai kode (250 = metalic, 252/253 = glow)
    vec4 oreTex = texture2DLod(s_MatTexture, v_texcoord0, 0.0);

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

    //metalic (hanya refleksi warna langit, tanpa awan)
    bool metalic = (int(oreTex.w * 255.0 + 0.5) == 250);
    if (v_ditheringAndMaskTinting.y > 0.5) {
        metalic = false;
    }
    float metalAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadow / 0.65);
    if (metalic && metalAktif > 0.001) {
        vec3 sebelumMetal = _finalColor.rgb;
        vec3 fr = fresnel(vec3_splat(0.3), NdotV);

        //lantai: pantulan langit biru, makin jauh makin putih
        if (N.y > 0.5) {
            _finalColor.rgb = mix(_finalColor.rgb, mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), saturate(lenKs / 70.0)), fr), siangd * (1.0 - hujand));
            _finalColor.rgb = mix(_finalColor.rgb, mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3(1.0, 0.8, 0.1), saturate(lenKs / 70.0)), fr), sored * (1.0 - hujand));
        }

        //sisi (dinding): warna atas dan bawah dicampur halus di sekitar tinggi mata
        if (max(abs(N.x), abs(N.z)) > 0.5) {
            float awEl = wp.y / max(lenWp, 0.001);
            float awAtas = smoothstep(-SISI_HALUS, SISI_HALUS, awEl);
            vec3 sisiAtas = mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), clamp(length(wp.xz) / max(abs(wp.y), 0.1) * 0.1, 0.05, 0.7));
            vec3 sisiBawah = vec3(212.0, 232.0, 255.0) / 255.0;
            _finalColor.rgb = mix(_finalColor.rgb, mix(sisiBawah, sisiAtas, awAtas), fr);

            vec3 soreAtas = vec3(0.1, 0.5, 1.0) * 1.2;
            vec3 soreBawah = vec3(1.0, 0.8, 0.1);
            _finalColor.rgb = mix(_finalColor.rgb, mix(soreBawah, soreAtas, awAtas), fr * sored * (1.0 - hujand));
        }

        _finalColor.rgb = mix(sebelumMetal, _finalColor.rgb, metalAktif);
    }

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
        float jitB = bayer8(gl_FragCoord.xy);
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
    }

    //nether
    if (nether) {
        _finalColor.rgb *= vec3(1.5, 0.9, 0.0) * (1.0 - sored);
        _finalColor.rgb += vec3_splat(0.2);
        _finalColor.rgb = Tm(_finalColor.rgb) * 0.9;
        vec3 fogn = mix(_finalColor.rgb, fclr.rgb * vec3(1.5, 1.0, 0.0), clamp(lenKs / fctl.z * 1.5, 0.1, 0.2));
        _finalColor.rgb = fogn;
    }

    //glow
    {
        float oreGlow = oreGlowCode(oreTex.w);
        if (v_ditheringAndMaskTinting.y > 0.5) {
            oreGlow = 0.0;
        }
        if (oreGlow > 0.0) {
            _finalColor.rgb += oreTex.rgb * oreGlow * GLOW_INTENSITY * glowFactor(lenKs, uv1.y, wk, malamd);
        }
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

    //metalic (AlphaTest: hanya refleksi warna langit di lantai, tanpa awan)
    bool metalic = (int(oreTex.w * 255.0 + 0.5) == 250);
    if (v_ditheringAndMaskTinting.y > 0.5) {
        metalic = false;
    }
    float metalAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadow / 0.65);
    if (metalic && metalAktif > 0.001) {
        vec3 sebelumMetal = _finalColor.rgb;
        if (N.y > 0.5) {
            _finalColor.rgb = mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), saturate(lenKs / 90.0)), fresnel(vec3_splat(0.3), NdotV));
        }
        _finalColor.rgb = mix(sebelumMetal, _finalColor.rgb, metalAktif);
    }

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
    }

    //glow
    {
        float oreGlow = oreGlowCode(oreTex.w);
        if (oreGlow > 0.0) {
            _finalColor.rgb += oreTex.rgb * oreGlow * GLOW_INTENSITY * glowFactor(lenKs, uv1.y, wk, malamd);
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
        float gradAir = mix(0.1, 1.0, smoothstep(AIR_GRAD_MULAI, AIR_GRAD_PENUH, lenWp)); // makin jauh makin biru
        vec3 airMalam = mix(vec3_splat(0.1), vec3(0.1, 0.5, 1.0), gradAir);
        _finalColor.rgb += airMalam * malamd * (1.0 - hujand) * (1.0 - goa) * 0.6;

        //transparansi air
        _finalColor.a = lenWp / 15.0;
        _finalColor.a *= 1.5;

        //wave air
        _finalColor.rgb += wave * 0.1 * vec3(0.3, 0.5, 1.0) * 0.7 * malamd;
        _finalColor.rgb += wave * 0.1 * vec3_splat(1.0) * 0.3 * hujand;

        //shadow block di air
        float shadowAir = smoothstep(1.0, 0.95, uv1.y + wave * 0.05);
        shadowAir *= (1.0 - uv1.x);
        shadowAir *= (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand);
        shadowAir = mix(0.0, 0.65, shadowAir);

        //refleksi awan di air (permukaan atas, bukan saat di bawah air)
        if (N.y > 0.5 && !udw) {
            // posisi awan dari jarak horizontal / tinggi awan TETAP (bukan dari sudut pantul),
            // jadi ukuran awan tidak berubah saat ketinggian kamera berubah, sama seperti bayangan awan di tanah
            float awDy = AWAN_AIR_TINGGI / sqrt(dot(wp.xz, wp.xz) + AWAN_AIR_TINGGI * AWAN_AIR_TINGGI);
            vec2 awBase = vec2(AWAN_SX * wp.x, AWAN_SZ * wp.z) / AWAN_AIR_TINGGI + wave * 0.05;
            float awDh = hashAwan(awBase * 999.0);
            float awShd = 0.0;
            float awMm = 1.0;
            float awSc = 1.4;
            for (int awI = 0; awI < 12; awI++) {
                awSc /= 1.009;
                awMm *= 0.96;
                float awAwan = step(0.88, hashAwan(floor(awBase * awSc + awDh * 0.02 + tm * AWAN_SPEED)));
                awShd = mix(awShd, awMm, awAwan);
            }
            // refleksi awan hilang saat hujan (hujand), malam (wk = tingkat terang siang), dan di bawah shadow block
            float awSiang = smoothstep(0.2, 0.8, wk);
            float awMask = awShd * smoothstep(0.0, 0.12, awDy) * AWAN_AIR_KUAT * (1.0 - shadowAir / 0.65) * (1.0 - hujand) * awSiang;
            vec3 awHasil = mix(_finalColor.rgb, vec3_splat(1.0), awMask);
            _finalColor.rgb = mix(_finalColor.rgb, awHasil, fresnel(vec3_splat(0.4), NdotV));
        }

        _finalColor.rgb = mix(_finalColor.rgb, vec3(0.0, 0.0, 0.08), shadowAir * (0.6 + wave * 0.4));

        //fresnel air
        _finalColor.rgb += fresnel(f0, NdotV) * 0.7 * (1.0 - malamd);

        //bintang di air (malam, hilang saat hujan, hanya permukaan atas)
        vec2 sg = p.xz * 5.0 + wave * 0.15; // jangkar p (tidak ikut pemain), sedikit goyang ikut ombak
        vec2 sid = floor(sg);
        vec2 sfr = fract(sg) - 0.5;
        float sr = hash12(sid + 17.0);
        vec2 sof = (vec2(hash12(sid + 3.1), hash12(sid + 8.7)) - 0.5) * 0.5;
        float sdot = smoothstep(0.12, 0.0, length(sfr - sof)) * step(0.93, sr);
        float skedip = 0.5 + 0.5 * sin(tm * 2.5 + sr * 60.0);
        float starAir = sdot * skedip * malamd * (1.0 - hujand) * (1.0 - goa) * step(0.5, N.y);
        starAir *= 1.0 - smoothstep(30.0, 80.0, lenWp); // jauh dipudarkan supaya tidak berpasir
        _finalColor.rgb += vec3(0.8, 0.9, 1.0) * starAir * 1.2;
        _finalColor.a = max(_finalColor.a, starAir * 0.9);

        //underwater (permukaan air dilihat dari bawah)
        float uw = float(fctl.x == 0.0 && fctl.y < 0.8 && (FogColor.z > FogColor.x || FogColor.y > FogColor.x));
        if (uw > 0.5 && N.y < -0.5) {
            _finalColor.rgb = mix(_finalColor.rgb, vec3(0.1, 0.45, 0.9), 0.85);
            _finalColor.rgb += fresnel(f0, NdotV) * 0.5 * (1.0 - malamd);

            //transparansi air
            _finalColor.a = lenWp / 15.0 + wave * 0.05;
            _finalColor.a *= 1.5;
        }
    }

    //glow
    {
        float oreGlow = oreGlowCode(oreTex.w);
        if (v_ditheringAndMaskTinting.y > 0.5) {
            oreGlow = 0.0;
        }
        if (oreGlow > 0.0) {
            _finalColor.rgb += oreTex.rgb * oreGlow * GLOW_INTENSITY * glowFactor(lenKs, uv1.y, wk, malamd);
        }
    }
#endif

    //fog
#if defined(OPAQUE)
    if (!(akhir)) {
        vec4 fgst = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(lenKs / 100.0)), (1.0 - hujand));
        _finalColor = fgst;
        _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(lenKs / 20.0)), dudan);
        // fog bawah air tebal
        if (udw) {
            _finalColor = mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), udwFog(lenKs));
        }
    }
#elif defined(ALPHA_TEST)
    _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(length(-ks) / 100.0)), (1.0 - hujand));
    _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(length(-ks) / 20.0)), hujand);
    // fog bawah air tebal
    if (udw) {
        _finalColor = mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), udwFog(lenKs));
    }
#elif defined(TRANSPARENT)
    _finalColor = mix(_finalColor, mix(_finalColor, v_fog * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(length(-wp) / 100.0)), (1.0 - hujand));
    // fog bawah air tebal
    if (udw) {
        _finalColor = mix(_finalColor, v_fog * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), udwFog(lenWp));
    }
#endif

#if defined(OPAQUE) || defined(ALPHA_TEST)
    //god rays (dipasang setelah fog supaya tidak tertelan fog jarak jauh)
    if (udw && wk > 0.05) {
        // hilang di kejauhan (dan tidak muncul tepat di depan kamera)
        float grJarak = smoothstep(1.0, GODRAY_DEKAT, lenWp) * (1.0 - smoothstep(GODRAY_JAUH_MIN, GODRAY_JAUH_MAX, lenWp));
        // laut dalam (1.0 - uv1.y) dan di bawah block: berkas dikalikan uv1.y (= v_lightmapUV.y)
        float grGate = grJarak * uv1.y;
        if (grGate > 0.001) {
            float grDens = godRayFog(p, wp, gl_FragCoord.xy);
            float grBawah = smoothstep(-0.7, -0.2, N.y);
            float grA = grDens * grGate * wk * grBawah * GODRAY_POWER;
            _finalColor.rgb = mix(_finalColor.rgb, vec3(0.5, 1.05, 1.2), saturate(grA));
        }
    }
#endif

    diffuse = _finalColor;
#endif

    gl_FragColor = diffuse;
#endif
}
