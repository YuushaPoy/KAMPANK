$input v_clipPosition, v_worldPosition, v_color0, v_ditheringAndMaskTinting, v_fog, v_lightmapUV, v_texcoord0, v_worldPos, p

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/DitherUtil.dragonh>

uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;

//fog bawah air
#define UDW_FOG_MAX   1.0
#define SHEET_PENANDA 249
#define SHEET_PX 32.0
#define SHEET_ATLAS vec2(0.0, 0.0)
//normalmap metalic
#define NORMALMAP_KUAT 0.2
#define NORMALMAP_FLIP vec2(1.0, 1.0)
#define NORMALMAP_SLICE vec2(0.5, 0.0)
#define NORMALMAP_TBN 0
#define NORMALMAP_SKY_KUAT 0.2
#define SNELL_SUDUT 0.661
#define SNELL_LEMBUT 0.03
#define SNELL_DISTORSI 0.02
#define SNELL_INDEKS 1.333
#define SNELL_LANGIT 1.0
#define SNELL_PUTIH 0.3
#define SNELL_PUSAT 3.0
#define SNELL_TEPI 0.6
#define SNELL_TEPI_MALAM 0.9
#define SNELL_MALAM_BIRU 0.45
#define SNELL_TEPI_MULAI 0.55
#define SNELL_BINTANG_SKALA 80.0
#define SNELL_BINTANG_DENSITAS 0.07
#define SNELL_BINTANG_TERANG 1.0
#define SNELL_GELAP 0.5
#define UDW_DENSITAS 0.09
#define UDW_WARNA vec3(0.02, 0.42, 0.38)
#define UDW_COLOR vec3(0.3, 1.1, 0.85)
#define UDW_SCATTER 6.0
#define UDW_TERANG 2.0
//fog hujan
#define FOG_HUJAN_JARAK 20.0
float hashLangit(vec2 q) {
    return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453);
}

float udwFog(float d) {
    return UDW_FOG_MAX * (1.0 - exp(-d * UDW_DENSITAS));
}

//falloff cahaya block
#define BLOCKLIGHT_CUT 0.2
#define BLOCKLIGHT_POW 1.6
float lightFalloff(float x) {
    x = saturate((x - BLOCKLIGHT_CUT) / (1.0 - BLOCKLIGHT_CUT));
    return pow(x, BLOCKLIGHT_POW);
}
uniform vec4 DitherParams;
uniform vec4 DitherParams2[3];
uniform vec4 ViewPositionAndTime;

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);
#if defined(SEASONS)
SAMPLER2D_AUTOREG(s_SeasonsTexture);
#endif

#if !defined(SEASONS) || defined(ALPHA_TEST)
    #define KODE_EXTRA 1
#endif




//warna terrain dimensi End
#define END_BRIGHT     4.0
#define END_SAT        0.7
#define END_LM_TINT    0.35
#define END_SHOULDER   0.7
#define END_BLOCKLIGHT 0.55
#define END_LIGHT_COL  vec3(1.1, 0.9, 1.55)
#define END_LIGHT_AMT  0.85
#define END_DIM        0.55

//metalic
#define SISI_HALUS 0.15

//fajar senja
#define SUN_TERANG vec3(1.3, 1.1, 0.8)
#define SUN_GELAP vec3(0.7, 0.72, 0.85)
#define SUN_GAIN 2.0
#define SORE_FOG_WARNA vec3(1.0, 0.82, 0.3)
#define SORE_FOG_KUAT 0.75
#define SORE_WARNA vec3(1.15, 1.0, 0.7)
#define SORE_KUAT 0.6
#define SORE_BATAS_A 0.35
#define SORE_BATAS_B 0.9
#define SUN_ARAH 0.0
#define SUN_LIHAT 4.0
#define SUN_FOGA 1.0
#define SUN_DEBUG 0.0

//gradasi biru air saat malam
#define AIR_GRAD_MULAI 20.0
#define AIR_GRAD_PENUH 100.0

//glow
#define GLOW_INTENSITY 0.85
#define GLOW_SIANG 0.35
#define GLOW_DENYUT 0.08
#define GLOW_LANGIT_MIN 0.55
#define GLOW_LANGIT_MAX 0.90
#define GLOW_MALAM_TERBUKA 1.0
#define GLOW_DASAR 0.25
#define GLOW_TETAP 1.0
#define GLOW_BLOOM_KUAT 1.6
#define GLOW_BLOOM_MULAI 0.4
#define GLOW_BLOOM_PUTIH 0.6

//bayangan awan
#define BAYANG_AWAN 1
#define AWAN_SPEED 0.04
#define BAYANG_AWAN_TINGGI 40.0
#define BAYANG_AWAN_KUAT 0.38
#define BAYANG_AWAN_DITHER 0.02

//refleksi awan di air
#define AWAN_SX 1.0
#define AWAN_SZ 1.0
#define AWAN_METAL_KUAT 1.0
#define AWAN_SPEED_DINDING 0.04
#define AWAN_AIR_KUAT 0.3
#define AWAN_AIR_TINGGI 10.0

//god rays bawah air
#define GODRAY_POWER 0.35
#define GODRAY_BATAS_A 0.25
#define GODRAY_BATAS_B 0.9
#define GODRAY_JITTER 0.5
#define GODRAY_DEKAT 8.0
#define GODRAY_JAUH_MIN 24.0
#define GODRAY_JAUH_MAX 48.0
#define GODRAY_GESER 0.10
#define GODRAY_PERIODE 8.0
#define GODRAY_MIRING 0.5
#define GODRAY_KEDIP 0.30

//ACESFilm tonemap
vec3 ACESFilm(vec3 x) {
    float a = 3.0;
    float b = 0.001;
    float c = 3.1;
    float d = 0.48;
    float e = 0.1;
    return ((x * (a * x + b)) / (x * (c * x + d) + e));
}

//Tm tonemap
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

//saturation
vec3 saturation(vec3 rgb, float adjustment) {
    vec3 W = vec3(0.2125, 0.7154, 0.0721);
    vec3 intensity = vec3_splat(dot(rgb, W));
    return mix(intensity, rgb, adjustment);
}

float endFogAmt(vec4 f) {
    float ungu = smoothstep(0.0, 0.012, min(f.r, f.b) - f.g);
    float gelap = 1.0 - smoothstep(0.05, 0.14, max(f.r, max(f.g, f.b)));
    return ungu * gelap;
}
bool endFog(vec4 f) {
    return endFogAmt(f) > 0.01;
}

#if defined(KODE_EXTRA)
//hash, fresnel, genangan
float hash12(vec2 q) {
    vec3 p3 = fract(vec3(q.xyx) * 0.13);
    p3 += dot(p3, p3.yzx + 3.333);
    return fract((p3.x + p3.y) * p3.z);
}

//dither Bayer 8x8
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

//air
vec3 satuR(vec3 v, float val) {
    vec3 sat = vec3_splat(dot(v, vec3_splat(1.0 / 3.0)));
    return mix(sat, v, val);
}

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

//ombak air
#define OMBAK_K 0.39269908
#define OMBAK_GESER 1.0
#define OMBAK_KUAT_SIANG 0.25
float ombakAir(vec2 pxz, float tm) {
    float g = tm * OMBAK_GESER;
    float w1 = sin(dot(pxz, vec2(2.0, 1.0)) * OMBAK_K + g * 1.3);
    float w2 = sin(dot(pxz, vec2(-1.0, 3.0)) * OMBAK_K - g * 1.7);
    float w3 = sin(dot(pxz, vec2(4.0, -3.0)) * OMBAK_K + g * 2.3);
    float h = w1 * 0.5 + w2 * 0.3 + w3 * 0.2;
    return 0.5 + 0.5 * h;
}

//glow
float oreGlowCode(float a) {
    int c = int(a * 255.0 + 0.5);
    return (c == 252) ? 1.0 : ((c == 253) ? 0.4 : 0.0);
}

//bloom
vec3 glowBloom(vec3 g) {
    g *= GLOW_BLOOM_KUAT;
    float l = dot(g, vec3(0.299, 0.587, 0.114));
    float w = smoothstep(GLOW_BLOOM_MULAI, 1.0, l) * GLOW_BLOOM_PUTIH;
    return mix(g, vec3_splat(l * 1.15), w);
}

float glowFactor(float lenK, float skyL, float wkS, float malamS) {
    float fade = 1.0 - smoothstep(0.6, 1.0, lenK / FogAndDistanceControl.z);
    float naungan = 1.0 - smoothstep(GLOW_LANGIT_MIN, GLOW_LANGIT_MAX, skyL);
    float malamTerbuka = (1.0 - smoothstep(0.1, 0.4, wkS)) * GLOW_MALAM_TERBUKA;
    naungan = max(naungan, malamTerbuka);
    float terang = mix(GLOW_SIANG, 1.0, malamS) * naungan;
    float pulse = 1.0 + GLOW_DENYUT * sin(ViewPositionAndTime.w * 1.5);
    return terang * fade * pulse;
}

float hashAwan(vec2 q) {
    return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453);
}

//bayangan awan
float awanBayang(vec2 awBase, vec2 fc) {
    float awDh = bayer8(fc);
    return step(0.88, hashAwan(floor(awBase * 1.33 + awDh * BAYANG_AWAN_DITHER + ViewPositionAndTime.w * AWAN_SPEED)));
}

#if defined(ALPHA_TEST) || defined(OPAQUE) || defined(TRANSPARENT)
//refleksi awan di logam
float awanLapis(vec2 awBase, float spd, vec2 fc) {
    float awDh = bayer8(fc);
    float awShd = 0.0;
    float awMm = 1.0;
    float awSc = 1.4;
    for (int awI = 0; awI < 12; awI++) {
        awSc /= 1.009;
        awMm *= 0.96;
        float awAwan = step(0.88, hashAwan(floor(awBase * awSc + awDh * 0.02 + ViewPositionAndTime.w * spd)));
        awShd = mix(awShd, awMm, awAwan);
    }
    return awShd;
}

float awanRefMetal(vec2 awH, float spd, vec2 fc) {
    return awanLapis(vec2(AWAN_SX * awH.x, AWAN_SZ * awH.y) / AWAN_AIR_TINGGI, spd, fc);
}

float awanRef(vec3 awR, float spd, vec2 fc) {
    vec2 awBase = vec2(AWAN_SX * awR.x, AWAN_SZ * awR.z) / max(awR.y, 0.02);
    return awanLapis(awBase, spd, fc) * smoothstep(0.0, 0.12, awR.y);
}

//caustic
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

#define RAY_D  vec3(0.324617, -0.927478, 0.185496)
#define RAY_T1 vec3(-0.943858, -0.330350, 0.0)
#define RAY_T2 vec3(0.061279, -0.175082, -0.982645)

float causticRay(vec3 pp, vec3 nn) {
    vec2 q = vec2(dot(pp, RAY_T1), dot(pp, RAY_T2));
    float c = smoothstep(0.2, 1.0, vorolit(q * 0.35));
    c *= mix(0.6, 1.0, saturate(dot(nn, -RAY_D)));
    return c;
}

float godRayFog(vec3 pp, vec3 ww, vec2 fc) {
    float tm = ViewPositionAndTime.w;
    float jit = bayer8(fc) * GODRAY_JITTER;
    float acc = 0.0;
    for (int i = 0; i < 6; i++) {
        float t = (float(i) + jit) / 6.0;
        vec3 sp = pp - ww * (1.0 - t);
        vec2 q = vec2(sp.x + sp.y * GODRAY_MIRING + tm * GODRAY_GESER, sp.z + tm * GODRAY_GESER * 0.4);
        float n = noiseTile(q, GODRAY_PERIODE);
        float kedip = 1.0 - GODRAY_KEDIP + GODRAY_KEDIP * (0.5 + 0.5 * sin(tm * 1.4 + n * 12.0));
        acc += smoothstep(GODRAY_BATAS_A, GODRAY_BATAS_B, n) * kedip;
    }
    return acc / 6.0;
}
#endif // ALPHA_TEST || OPAQUE || TRANSPARENT
#endif // KODE_EXTRA

//cotangent
vec3 cotangent_frame(vec3 N, vec3 normalmap) {
    float a = abs(N.y) + N.z;
    return vec3(normalmap.x * a + normalmap.z * N.x,
                normalmap.z * N.y,
                -normalmap.x * N.x + normalmap.y * N.y + normalmap.z * N.z);
}

//tbn
vec3 tbn_frame(vec3 T, vec3 B, vec3 N, vec3 normalmap) {
    return T * normalmap.x + B * normalmap.y + N * normalmap.z;
}

//tangent sumbu (gaya Nori)
vec3 tangentSumbu(vec3 nn) {
    vec3 t = vec3(1.0, 0.0, 0.0);
    if (nn.x > 0.0) {
        t = vec3(0.0, 0.0, -1.0);
    } else if (-nn.x > 0.0) {
        t = vec3(0.0, 0.0, 1.0);
    } else if (nn.z < 0.0) {
        t = vec3(-1.0, 0.0, 0.0);
    }
    return t;
}

//normalmap
vec3 normalmapDecode(vec4 tex) {
    if (tex.r <= 0.0 && tex.g <= 0.0 && tex.b <= 0.0) {
        tex.rgb = vec3(0.5, 0.5, 1.0);
    }
    vec3 nm = tex.rgb * 2.0 - 1.0;
    nm.xy *= NORMALMAP_KUAT * NORMALMAP_FLIP;
    nm.z = max(nm.z, 0.2);
    return normalize(nm);
}

vec3 normalmapN(vec3 N, vec3 T, vec3 B, vec3 normalmap) {
#if NORMALMAP_TBN > 0
    return normalize(tbn_frame(T, B, N, normalmap));
#else
    return normalize(cotangent_frame(N, normalmap));
#endif
}

//sheet
vec2 sheetLocal(vec3 pp, vec3 nn, vec2 uv) {
    vec3 an = abs(nn);
    vec2 w = (an.y > 0.5) ? pp.xz : ((an.x > 0.5) ? vec2(pp.z, pp.y) : vec2(pp.x, pp.y));
    vec2 dwx = dFdx(w);
    vec2 dwy = dFdy(w);
    float cuA = dFdx(uv.x) * dwx.x + dFdy(uv.x) * dwy.x;
    float cuB = dFdx(uv.x) * dwx.y + dFdy(uv.x) * dwy.y;
    float cvA = dFdx(uv.y) * dwx.x + dFdy(uv.y) * dwy.x;
    float cvB = dFdx(uv.y) * dwx.y + dFdy(uv.y) * dwy.y;
    bool lurus = (abs(cuA) + abs(cvB)) >= (abs(cuB) + abs(cvA));
    float su = lurus ? cuA : cuB;
    float sv = lurus ? cvB : cvA;
    float wu = lurus ? w.x : w.y;
    float wv = lurus ? w.y : w.x;
    return vec2(su >= 0.0 ? fract(wu) : fract(-wu), sv >= 0.0 ? fract(wv) : fract(-wv));
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

    //sheet
    vec2 uvS = v_texcoord0;
    bool sheetOn = false;
    vec2 sheetLoc = vec2(0.0, 0.0);
    vec4 sheetData = vec4(0.0, 0.0, 0.0, 0.0);
    vec3 sheetEmi = vec3(0.0, 0.0, 0.0);
    vec4 sheetNrm = vec4(0.5, 0.5, 1.0, 1.0);
#if defined(KODE_EXTRA) && (defined(OPAQUE) || defined(ALPHA_TEST))
    vec3 sheetNg = normalize(cross(dFdx(p.xyz), dFdy(p.xyz)));
    sheetLoc = min(sheetLocal(p.xyz, sheetNg, v_texcoord0), vec2(0.999, 0.999));
    vec4 sheetRaw = texture2DLod(s_MatTexture, v_texcoord0, 0.0);
    if (int(sheetRaw.w * 255.0 + 0.5) == SHEET_PENANDA) {
        sheetOn = true;
        vec2 sheetAtlas = vec2(textureSize(s_MatTexture, 0));
        if (SHEET_ATLAS.x > 0.5) {
            sheetAtlas = SHEET_ATLAS;
        }
        vec2 sheetExt = vec2(SHEET_PX, SHEET_PX) / sheetAtlas;
        vec2 sheetOrg = v_texcoord0 - sheetLoc * sheetExt;
        uvS = sheetOrg + sheetLoc * 0.5 * sheetExt;
        sheetData = texture2DLod(s_MatTexture, sheetOrg + (vec2(0.0, 0.5) + sheetLoc * 0.5) * sheetExt, 0.0);
        sheetEmi = texture2DLod(s_MatTexture, sheetOrg + (vec2(0.5, 0.5) + sheetLoc * 0.5) * sheetExt, 0.0).rgb;
        sheetNrm = texture2DLod(s_MatTexture, sheetOrg + (NORMALMAP_SLICE + sheetLoc * 0.5) * sheetExt, 0.0);
    }
#endif

    vec4 diffuse = texture2D(s_MatTexture, uvS);

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
        //AO
        vec3 _aoMul = v_color0.rgb;
        if (abs(v_color0.g - v_color0.r) < 0.002) {
            float ao = mix(0.55, 0.8, smoothstep(0.3, 0.7, v_color0.g));
            float terbuka = smoothstep(0.55, 0.9, v_lightmapUV.y);
            terbuka = max(terbuka, step(FogAndDistanceControl.x, 0.00999) * (1.0 - endFogAmt(v_fog)));
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
    float _endF = endFogAmt(v_fog);
    vec2 _lmUV = v_lightmapUV;
    _lmUV.x = lightFalloff(_lmUV.x);
    _lmUV.x *= mix(1.0, END_BLOCKLIGHT, _endF);
    vec3 _lm = texture2D(s_LightMapTexture, _lmUV).rgb;
    if (_endF > 0.001) {
        float _lmL = dot(_lm, vec3(0.2125, 0.7154, 0.0721));
        vec3 _lmE = mix(vec3_splat(_lmL), _lm, END_LM_TINT);
        _lmE = mix(_lmE, _lmL * END_LIGHT_COL, saturate(v_lightmapUV.x) * END_LIGHT_AMT);
        _lm = mix(_lm, _lmE, _endF);
    }
    diffuse.rgb *= _lm;
#else
    vec2 _lmUV2 = v_lightmapUV;
    _lmUV2.x = lightFalloff(_lmUV2.x);
    diffuse.rgb *= texture2D(s_LightMapTexture, _lmUV2).rgb;
#endif
    diffuse.rgb = mix(diffuse.rgb, FogColor.rgb, v_fog.a);

#if defined(KODE_EXTRA)
    vec4 _finalColor = diffuse;

    //var
#if defined(SEASONS)
    vec4 color = vec4(0.0, 1.0, 0.0, 1.0);
    vec4 kolor = color;
#else
    vec4 color = v_color0;
    vec4 kolor = v_color0;
#endif
    vec3 Ng = normalize(cross(dFdx(p.xyz), dFdy(p.xyz)));
    vec3 N = Ng;
    vec3 nmT = vec3(1.0, 0.0, 0.0);
    vec3 nmB = vec3(0.0, 0.0, 1.0);
#if NORMALMAP_TBN == 1
    nmT = tangentSumbu(Ng);
    nmB = normalize(cross(nmT, Ng));
#elif NORMALMAP_TBN == 2
    {
        vec3 dp1 = dFdx(p.xyz);
        vec3 dp2 = dFdy(p.xyz);
        vec2 duv1 = dFdx(v_texcoord0);
        vec2 duv2 = dFdy(v_texcoord0);
        vec3 dp2perp = cross(dp2, Ng);
        vec3 dp1perp = cross(Ng, dp1);
        nmT = dp2perp * duv1.x + dp1perp * duv2.x;
        nmB = dp2perp * duv1.y + dp1perp * duv2.y;
        float invmax = inversesqrt(max(max(dot(nmT, nmT), dot(nmB, nmB)), 0.000000001));
        nmT *= invmax;
        nmB *= invmax;
    }
#endif
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
    vec4 oreTex = texture2DLod(s_MatTexture, uvS, 0.0);
    if (sheetOn) {
        float sheetGlow = step(0.004, max(sheetEmi.r, max(sheetEmi.g, sheetEmi.b)));
        oreTex = vec4(sheetEmi, mix(249.0, 252.0, sheetGlow) / 255.0);
    }

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
    float endF = endFogAmt(fclr);
    bool akhir = endF > 0.01;
#if defined(TRANSPARENT)
    bool udw = (fctl.x == 0.0) && !akhir;
#else
    bool udw = (fctl.x < 0.01) && !akhir;
#endif
    float isnether = step(fclr.b, fclr.r) * step(fclr.r, 0.4999) * step(fclr.b, 0.1999);
    float isudw = step(fctl.x, 0.00999) * (akhir ? 0.0 : 1.0);
    float isakhir = endF;

    //dalam air
    float uwTop = smoothstep(0.9275, 0.9365, uv1.y);
    float uwBawah = 1.0 - smoothstep(0.7, 1.2, wp.y);
    float uwIn = mix(1.0, uwBawah, uwTop);

    //fog underwater
    float udwDay = smoothstep(0.1, 0.7, wk);
    vec3 udwFogCol = vec3_splat(0.0);
    if (udw) {
        vec3 udwV = normalize(wp);
        float udwHg = 0.64 / (12.566 * pow(max(1.36 - 1.2 * udwV.y, 0.01), 1.5));
        udwFogCol = UDW_WARNA * (0.05 + udwDay * 0.2) * UDW_TERANG;
        udwFogCol *= 1.0 + UDW_SCATTER * udwHg * udwDay;
    }

    //deteksi fajar dan senja
    float duskK = pow(max(min(1.0 - FogColor.b * 1.2, 1.0), 0.0), 0.5) * uv1.y;
    float fogA = mix(1.0, FogColor.a, SUN_FOGA);
    vec3 camRz = vec3(mul(u_view, vec4(1.0, 0.0, 0.0, 0.0)).z, mul(u_view, vec4(0.0, 1.0, 0.0, 0.0)).z, mul(u_view, vec4(0.0, 0.0, 1.0, 0.0)).z);
    vec3 camFwd = normalize(camRz) * ((dot(camRz, wp) < 0.0) ? -1.0 : 1.0);
    float sunLihat = clamp((camFwd.x - camFwd.z) * SUN_LIHAT, -1.0, 1.0);
    bool sunManual = abs(SUN_ARAH) > 0.5;
    float sideR = sunManual ? step(0.0, SUN_ARAH) : saturate(sunLihat);
    float sideS = sunManual ? 1.0 - sideR : saturate(-sunLihat);
    float sunrise = duskK * fogA * sideR;
    sunrise *= uv1.y;
    float sunset = duskK * fogA * sideS;
    sunset *= uv1.y;
    float twiGate = (1.0 - hujand) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - isudw) * smoothstep(0.15, 0.4, max(fclr.r, max(fclr.g, fclr.b)));
    sunrise *= twiGate;
    sunset *= twiGate;
    float duskAmt = saturate(sunrise + sunset);
    float duskW = saturate(duskAmt * SUN_GAIN);
    float sunSide = (sunrise - sunset) / max(sunrise + sunset, 0.0001);
    float sunFace = saturate(max(max(sunSide * N.x, -sunSide * N.z), N.y)) * duskW;
    float sunDark = saturate(max(max(-sunSide * N.x, sunSide * N.z), -N.y)) * duskW;
    float sunDbg = step(0.5, SUN_DEBUG) * (1.0 - step(1.5, SUN_DEBUG));
    float soreHujan = (1.0 - smoothstep(0.0, 0.35, hujand)) * smoothstep(0.05, 0.2, max(fclr.r, max(fclr.g, fclr.b)) - min(fclr.r, min(fclr.g, fclr.b)));
    float soreWin = smoothstep(SORE_BATAS_A, SORE_BATAS_B, 4.0 * wk * (1.0 - wk)) * soreHujan;
    vec4 fogS = mix(v_fog, vec4(SORE_FOG_WARNA, v_fog.a), duskW * SORE_FOG_KUAT * soreWin);


#if defined(OPAQUE)
    float dudan = smoothstep(0.55, 0.1, fctl.r) * uv1.y;

    //dirlight
    float dir = max(N.x, -N.z);
    _finalColor.xyz *= mix(vec3_splat(0.75), vec3_splat(0.65), dir * (1.0 - pow(v_lightmapUV.x * 0.9, 4.7)) * (1.0 - goa) * (1.0 - hujand) * (1.0 - malamd) * (1.0 - duskW));

    //dirlight fajar senja
    float dusG = (1.0 - pow(v_lightmapUV.x * 0.9, 4.7)) * (1.0 - goa) * (1.0 - hujand);
    _finalColor.xyz *= mix(vec3_splat(1.0), SUN_TERANG, sunFace * dusG);
    _finalColor.xyz *= mix(vec3_splat(1.0), SUN_GELAP, sunDark * dusG);
    _finalColor.xyz *= mix(vec3_splat(1.0), SORE_WARNA, duskW * SORE_KUAT * soreWin);
    _finalColor.xyz = mix(_finalColor.xyz, vec3(1.0, 0.0, 0.0), sunFace * sunDbg);
    _finalColor.xyz = mix(_finalColor.xyz, vec3(0.0, 0.0, 1.0), sunDark * sunDbg);


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

    //bayangan awan
#if BAYANG_AWAN
    if (N.y > -0.5) {
        float bAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadowRaw);
        if (bAktif > 0.001) {
            float bDy = BAYANG_AWAN_TINGGI / sqrt(dot(wp.xz, wp.xz) + BAYANG_AWAN_TINGGI * BAYANG_AWAN_TINGGI);
            float bMask = awanBayang(wp.xz / BAYANG_AWAN_TINGGI, gl_FragCoord.xy) * smoothstep(0.0, 0.12, bDy);
            _finalColor.rgb = mix(_finalColor.rgb, vec3(0.0, 0.0, 0.12), BAYANG_AWAN_KUAT * bMask * bAktif);
        }
    }
#endif

    //metalic
    bool metalic = (int(oreTex.w * 255.0 + 0.5) == 250) || (sheetOn && sheetData.g > 0.5);
    if (v_ditheringAndMaskTinting.y > 0.5) {
        metalic = false;
    }
    float metalAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadow / 0.65);
    if (metalic && metalAktif > 0.001) {
        vec3 sebelumMetal = _finalColor.rgb;

        //normalmap
        vec3 Nm = N;
        if (sheetOn) {
            Nm = normalmapN(N, nmT, nmB, normalmapDecode(sheetNrm));
        }
        float NdotVm = max(0.001, dot(Nm, V));
        vec3 fr = fresnel(vec3_splat(0.3), NdotVm);
        vec3 Rn = reflect(-V, N);
        vec3 Rm = reflect(-V, Nm);

        //lantai
        if (N.y > 0.5) {
            _finalColor.rgb = mix(_finalColor.rgb, mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), saturate(lenKs / 70.0 + (Rn.y - Rm.y) * NORMALMAP_SKY_KUAT)), fr), siangd * (1.0 - hujand));
            _finalColor.rgb = mix(_finalColor.rgb, mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3(1.0, 0.8, 0.1), saturate(lenKs / 70.0 + (Rn.y - Rm.y) * NORMALMAP_SKY_KUAT)), fr), sored * (1.0 - hujand));

            //awan ref
            if (!udw) {
                vec3 awRv = reflect(wp, Nm);
                if (awRv.y > 0.0) {
                    float awDy = AWAN_AIR_TINGGI / sqrt(dot(awRv.xz, awRv.xz) + AWAN_AIR_TINGGI * AWAN_AIR_TINGGI);
                    float awMask = awanRefMetal(awRv.xz, 0.04, gl_FragCoord.xy) * smoothstep(0.0, 0.12, awDy) * AWAN_METAL_KUAT;
                    vec3 awHasil = mix(_finalColor.rgb, vec3_splat(1.0), awMask);
                    _finalColor.rgb = mix(_finalColor.rgb, awHasil, fr);
                }
            }
        }

        //sisi
        if (max(abs(N.x), abs(N.z)) > 0.5) {
            float awEl = Rm.y;
            float awAtas = smoothstep(-SISI_HALUS, SISI_HALUS, awEl);
            vec3 sisiAtas = mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), clamp(length(Rm.xz) / max(abs(Rm.y), 0.1 / max(lenWp, 0.001)) * 0.1, 0.05, 0.7));
            vec3 sisiBawah = vec3(212.0, 232.0, 255.0) / 255.0;
            _finalColor.rgb = mix(_finalColor.rgb, mix(sisiBawah, sisiAtas, awAtas), fr);

            vec3 soreAtas = vec3(0.1, 0.5, 1.0) * 1.2;
            vec3 soreBawah = vec3(1.0, 0.8, 0.1);
            _finalColor.rgb = mix(_finalColor.rgb, mix(soreBawah, soreAtas, awAtas), fr * sored * (1.0 - hujand));
        }

        //awan ref
        if (wp.y > -0.05 && !udw && max(abs(N.x), abs(N.z)) > 0.5) {
            vec3 awR = reflect(-V, Nm);
            if (awR.y > 0.0) {
                float awMask = awanRef(awR, AWAN_SPEED_DINDING, gl_FragCoord.xy);
                vec3 awHasil = mix(_finalColor.rgb, vec3_splat(1.0), awMask);
                _finalColor.rgb = mix(_finalColor.rgb, awHasil, fr);
            }
        }

        _finalColor.rgb = mix(sebelumMetal, _finalColor.rgb, metalAktif);
    }

    //hujan
    if (!(udw && nether && akhir)) {
        _finalColor.rgb += vec3(-0.2, -0.1, -0.05) * dudan * (1.0 - malamd);

        //genangan
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
        _finalColor.rgb *= UDW_COLOR;
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
            float gf = glowFactor(lenKs, uv1.y, wk, malamd);
            _finalColor.rgb = mix(_finalColor.rgb, oreTex.rgb * GLOW_DASAR, saturate(oreGlow * gf * GLOW_TETAP));
            _finalColor.rgb += glowBloom(oreTex.rgb * oreGlow * GLOW_INTENSITY * gf);
        }
    }

    //end
    if (akhir) {
        vec3 ec = _finalColor.rgb * END_BRIGHT * endK;
        ec = saturation(ec, END_SAT);
        vec3 eo = max(ec - vec3_splat(END_SHOULDER), vec3_splat(0.0));
        float ek = 1.0 - END_SHOULDER;
        ec = min(ec, vec3_splat(END_SHOULDER)) + ek * (vec3_splat(1.0) - exp(-eo / ek));

        float drl = max(max(-N.x, -N.z), -N.y);
        ec *= mix(vec3_splat(0.95), vec3_splat(0.5), drl * (1.0 - v_lightmapUV.x));
        _finalColor.rgb = mix(_finalColor.rgb, ec, endF);
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
        _finalColor.xyz *= mix(vec3_splat(0.95), vec3_splat(0.558), dir * (1.0 - v_lightmapUV.x) * (1.0 - dirAlpha) * (1.0 - duskW));
    }

    //dirlight fajar senja
    float dusG = (1.0 - pow(v_lightmapUV.x * 0.9, 4.7)) * (1.0 - goa) * (1.0 - hujand);
    _finalColor.xyz *= mix(vec3_splat(1.0), SUN_TERANG, sunFace * dusG);
    _finalColor.xyz *= mix(vec3_splat(1.0), SUN_GELAP, sunDark * dusG);
    _finalColor.xyz *= mix(vec3_splat(1.0), SORE_WARNA, duskW * SORE_KUAT * soreWin);
    _finalColor.xyz = mix(_finalColor.xyz, vec3(1.0, 0.0, 0.0), sunFace * sunDbg);
    _finalColor.xyz = mix(_finalColor.xyz, vec3(0.0, 0.0, 1.0), sunDark * sunDbg);
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

    //bayangan awan
#if BAYANG_AWAN
    if (N.y > -0.5) {
        float bAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadowRaw);
        if (bAktif > 0.001) {
            float bDy = BAYANG_AWAN_TINGGI / sqrt(dot(wp.xz, wp.xz) + BAYANG_AWAN_TINGGI * BAYANG_AWAN_TINGGI);
            float bMask = awanBayang(wp.xz / BAYANG_AWAN_TINGGI, gl_FragCoord.xy) * smoothstep(0.0, 0.12, bDy);
            _finalColor.rgb = mix(_finalColor.rgb, vec3(0.0, 0.0, 0.12), BAYANG_AWAN_KUAT * bMask * bAktif);
        }
    }
#endif

    //metalic
    bool metalic = (int(oreTex.w * 255.0 + 0.5) == 250) || (sheetOn && sheetData.g > 0.5);
    if (v_ditheringAndMaskTinting.y > 0.5) {
        metalic = false;
    }
    float metalAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadow / 0.65);
    if (metalic && metalAktif > 0.001) {
        vec3 sebelumMetal = _finalColor.rgb;

        //normalmap
        vec3 Nm = N;
        if (sheetOn) {
            Nm = normalmapN(N, nmT, nmB, normalmapDecode(sheetNrm));
        }
        float NdotVm = max(0.001, dot(Nm, V));
        vec3 fr = fresnel(vec3_splat(0.3), NdotVm);
        vec3 Rn = reflect(-V, N);
        vec3 Rm = reflect(-V, Nm);

        //lantai
        if (N.y > 0.5) {
            _finalColor.rgb = mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), saturate(lenKs / 90.0 + (Rn.y - Rm.y) * NORMALMAP_SKY_KUAT)), fr);

            //awan ref
            if (!udw) {
                vec3 awRv = reflect(wp, Nm);
                if (awRv.y > 0.0) {
                    float awDy = AWAN_AIR_TINGGI / sqrt(dot(awRv.xz, awRv.xz) + AWAN_AIR_TINGGI * AWAN_AIR_TINGGI);
                    float awMask = awanRefMetal(awRv.xz, 0.04, gl_FragCoord.xy) * smoothstep(0.0, 0.12, awDy) * AWAN_METAL_KUAT;
                    vec3 awHasil = mix(_finalColor.rgb, vec3_splat(1.0), awMask);
                    _finalColor.rgb = mix(_finalColor.rgb, awHasil, fr);
                }
            }
        }

        //sisi
        if (max(abs(N.x), abs(N.z)) > 0.5) {
            float awEl = Rm.y;
            float awAtas = smoothstep(-SISI_HALUS, SISI_HALUS, awEl);
            vec3 sisiAtas = mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), clamp(length(Rm.xz) / max(abs(Rm.y), 0.1 / max(lenWp, 0.001)) * 0.1, 0.05, 0.7));
            vec3 sisiBawah = vec3(212.0, 232.0, 255.0) / 255.0;
            _finalColor.rgb = mix(_finalColor.rgb, mix(sisiBawah, sisiAtas, awAtas), fr);

            vec3 soreAtas = vec3(0.1, 0.5, 1.0) * 1.2;
            vec3 soreBawah = vec3(1.0, 0.8, 0.1);
            _finalColor.rgb = mix(_finalColor.rgb, mix(soreBawah, soreAtas, awAtas), fr * sored * (1.0 - hujand));
        }

        //awan ref
        if (wp.y > -0.05 && !udw && max(abs(N.x), abs(N.z)) > 0.5) {
            vec3 awR = reflect(-V, Nm);
            if (awR.y > 0.0) {
                float awMask = awanRef(awR, AWAN_SPEED_DINDING, gl_FragCoord.xy);
                vec3 awHasil = mix(_finalColor.rgb, vec3_splat(1.0), awMask);
                _finalColor.rgb = mix(_finalColor.rgb, awHasil, fr);
            }
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

        float drl = max(-N.x, -N.z);
        ec *= mix(vec3_splat(0.85), vec3_splat(0.65), drl * (1.0 - pow(v_lightmapUV.x * 0.9, 4.7)));
        _finalColor.rgb = mix(_finalColor.rgb, ec, endF);
    }

    //underwater
    if (udw) {
        _finalColor.rgb *= UDW_COLOR;
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
            float gf = glowFactor(lenKs, uv1.y, wk, malamd);
            _finalColor.rgb = mix(_finalColor.rgb, oreTex.rgb * GLOW_DASAR, saturate(oreGlow * gf * GLOW_TETAP));
            _finalColor.rgb += glowBloom(oreTex.rgb * oreGlow * GLOW_INTENSITY * gf);
        }
    }

#elif defined(TRANSPARENT)
    bool banyu = (kolor.b > kolor.g || kolor.b > kolor.r);

    //coloring, saturation and tonemap
    if (!(banyu)) {
        if (!akhir) {
            _finalColor.rgb *= vec3_splat(1.7);
            _finalColor.rgb = saturation(_finalColor.rgb, 1.2);
            if (!(udw)) {
                _finalColor.xyz *= ACESFilm(_finalColor.xyz);
            }
            _finalColor.xyz = Tm(_finalColor.xyz);
        }

        //shadow
        float shadowRaw = smoothstep(1.0, 0.95, uv1.y);
        float shadow = shadowRaw;
        shadow *= (1.0 - uv1.x);
        shadow *= (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand);
        shadow = mix(0.0, 0.65, shadow);

        //bayangan awan
    #if BAYANG_AWAN
        if (N.y > -0.5) {
            float bAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadowRaw);
            if (bAktif > 0.001) {
                float bDy = BAYANG_AWAN_TINGGI / sqrt(dot(wp.xz, wp.xz) + BAYANG_AWAN_TINGGI * BAYANG_AWAN_TINGGI);
                float bMask = awanBayang(wp.xz / BAYANG_AWAN_TINGGI, gl_FragCoord.xy) * smoothstep(0.0, 0.12, bDy);
                _finalColor.rgb = mix(_finalColor.rgb, vec3(0.0, 0.0, 0.12), BAYANG_AWAN_KUAT * bMask * bAktif);
            }
        }
    #endif

        //refleksi
        float refAktif = smoothstep(0.05, 0.3, wk) * (1.0 - hujand) * (1.0 - isudw) * (1.0 - isnether) * (1.0 - isakhir) * (1.0 - shadow / 0.65);
        vec3 sebelumRef = _finalColor.rgb;
        vec3 fr = fresnel(vec3_splat(0.3), NdotV);

        //lantai
        if (N.y > 0.5) {
            _finalColor.rgb = mix(_finalColor.rgb, mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), saturate(lenWp / 70.0)), fr), siangd * (1.0 - hujand));
            _finalColor.rgb = mix(_finalColor.rgb, mix(_finalColor.rgb, mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3(1.0, 0.8, 0.1), saturate(lenWp / 70.0)), fr), sored * (1.0 - hujand));

            //awan ref
            if (!udw) {
                float awDy = AWAN_AIR_TINGGI / sqrt(dot(wp.xz, wp.xz) + AWAN_AIR_TINGGI * AWAN_AIR_TINGGI);
                float awShd = awanLapis(vec2(AWAN_SX * wp.x, AWAN_SZ * wp.z) / AWAN_AIR_TINGGI, AWAN_SPEED, gl_FragCoord.xy);
                float awSiang = smoothstep(0.2, 0.8, wk);
                float awMask = awShd * smoothstep(0.0, 0.12, awDy) * AWAN_AIR_KUAT * (1.0 - shadow / 0.65) * (1.0 - hujand) * awSiang;
                vec3 awHasil = mix(_finalColor.rgb, vec3_splat(1.0), awMask);
                _finalColor.rgb = mix(_finalColor.rgb, awHasil, fresnel(vec3_splat(0.4), NdotV));
            }
        }

        //sisi
        if (max(abs(N.x), abs(N.z)) > 0.5) {
            float awEl = wp.y / max(lenWp, 0.001);
            float awAtas = smoothstep(-SISI_HALUS, SISI_HALUS, awEl);
            vec3 sisiAtas = mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), clamp(length(wp.xz) / max(abs(wp.y), 0.1) * 0.1, 0.05, 0.7));
            vec3 sisiBawah = vec3(212.0, 232.0, 255.0) / 255.0;
            _finalColor.rgb = mix(_finalColor.rgb, mix(sisiBawah, sisiAtas, awAtas), fr);

            vec3 soreAtas = vec3(0.1, 0.5, 1.0) * 1.2;
            vec3 soreBawah = vec3(1.0, 0.8, 0.1);
            _finalColor.rgb = mix(_finalColor.rgb, mix(soreBawah, soreAtas, awAtas), fr * sored * (1.0 - hujand));

            //awan ref
            if (wp.y > -0.05 && !udw) {
                float awMask = awanRef(reflect(-V, N), AWAN_SPEED_DINDING, gl_FragCoord.xy);
                _finalColor.rgb = mix(_finalColor.rgb, mix(_finalColor.rgb, vec3_splat(1.0), awMask), fr);
            }
        }
        _finalColor.rgb = mix(sebelumRef, _finalColor.rgb, refAktif);
    }

    //air
    if (banyu) {
        vec3 f0 = vec3_splat(0.05);
        float tm = ViewPositionAndTime.w;

        //gambar ombak
        float wave = ombakAir(p.xz, tm) * 0.85;

        //warna air
        _finalColor = vec4(0.3, 0.5, 1.0, 1.0) * 0.7 * siangd * (1.0 - hujand);
        _finalColor += vec4(0.3, 0.5, 0.7, 1.2) * 0.5 * malamd * (1.0 - hujand);
        _finalColor.rgb += vec3_splat(0.5) * 0.3 * hujand;
        _finalColor.rgb += vec3(0.1, 0.5, 1.0) * sored * (1.0 - hujand);
        _finalColor.rgb = satuR(_finalColor.rgb, 1.2) * siangd * (1.0 - goa);
        _finalColor.rgb += vec3(0.3, 0.5, 0.7) * goa;

        //gradasi malam
        float gradAir = mix(0.1, 1.0, smoothstep(AIR_GRAD_MULAI, AIR_GRAD_PENUH, lenWp));
        vec3 airMalam = mix(vec3_splat(0.1), vec3(0.1, 0.5, 1.0), gradAir);
        _finalColor.rgb += airMalam * malamd * (1.0 - hujand) * (1.0 - goa) * 0.6;

        //transparansi air
        _finalColor.a = lenWp / 15.0;
        _finalColor.a *= 1.5;

        //wave air
        _finalColor.rgb += wave * 0.1 * vec3(0.3, 0.5, 1.0) * 0.7 * malamd;
        _finalColor.rgb += wave * 0.1 * vec3_splat(1.0) * 0.3 * hujand;

        //warna air hujan
        float rdY = normalize(wp).y;
        float rGrad = clamp(sqrt(max(1.0 - rdY * rdY, 0.0)) * 1.6, 0.1, 1.0);
        vec3 airHujan = mix(vec3_splat(0.45), vec3_splat(0.2), rGrad);
        _finalColor.rgb = mix(_finalColor.rgb, airHujan + wave * 0.03, hujand * smoothstep(0.1, 0.4, wk) * (1.0 - isudw));
        //riak siang hari
        _finalColor.rgb += (wave - 0.42) * 0.5 * OMBAK_KUAT_SIANG * vec3(0.45, 0.7, 1.0) * siangd * (1.0 - hujand);

        //shadow block di air
        float shadowAir = smoothstep(1.0, 0.95, uv1.y);
        shadowAir *= (1.0 - uv1.x);
        shadowAir *= (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand) * (1.0 - hujand);
        shadowAir = mix(0.0, 0.65, shadowAir);

        //refleksi awan di air
        if (N.y > 0.5 && !udw) {
            float awDy = AWAN_AIR_TINGGI / sqrt(dot(wp.xz, wp.xz) + AWAN_AIR_TINGGI * AWAN_AIR_TINGGI);
            vec2 awBase = vec2(AWAN_SX * wp.x, AWAN_SZ * wp.z) / AWAN_AIR_TINGGI + wave * 0.05;
            float awDh = bayer8(gl_FragCoord.xy);
            float awShd = 0.0;
            float awMm = 1.0;
            float awSc = 1.4;
            for (int awI = 0; awI < 12; awI++) {
                awSc /= 1.009;
                awMm *= 0.96;
                float awAwan = step(0.88, hashAwan(floor(awBase * awSc + awDh * 0.02 + tm * AWAN_SPEED)));
                awShd = mix(awShd, awMm, awAwan);
            }
            float awSiang = smoothstep(0.2, 0.8, wk);
            float awMask = awShd * smoothstep(0.0, 0.12, awDy) * AWAN_AIR_KUAT * (1.0 - shadowAir / 0.65) * (1.0 - hujand) * awSiang;
            vec3 awHasil = mix(_finalColor.rgb, vec3_splat(1.0), awMask);
            _finalColor.rgb = mix(_finalColor.rgb, awHasil, fresnel(vec3_splat(0.4), NdotV));
        }

        _finalColor.rgb = mix(_finalColor.rgb, _finalColor.rgb * vec3(0.7, 0.82, 1.0), shadowAir * (0.6 + wave * 0.4));

        //fresnel air
        _finalColor.rgb += fresnel(f0, NdotV) * 0.7 * (1.0 - malamd);

        //bintang di air
        vec2 sg = p.xz * 5.0 + wave * 0.15;
        vec2 sid = floor(sg);
        vec2 sfr = fract(sg) - 0.5;
        float sr = hash12(sid + 17.0);
        vec2 sof = (vec2(hash12(sid + 3.1), hash12(sid + 8.7)) - 0.5) * 0.5;
        float sdot = smoothstep(0.12, 0.0, length(sfr - sof)) * step(0.93, sr);
        float skedip = 0.5 + 0.5 * sin(tm * 2.5 + sr * 60.0);
        float starAir = sdot * skedip * malamd * (1.0 - hujand) * (1.0 - goa) * step(0.5, N.y);
        starAir *= 1.0 - smoothstep(30.0, 80.0, lenWp);
        _finalColor.rgb += vec3(0.8, 0.9, 1.0) * starAir * 1.2;
        _finalColor.a = max(_finalColor.a, starAir * 0.9);

        //underwater
        float uw = float(fctl.x == 0.0 && fctl.y < 0.8 && (FogColor.z > FogColor.x || FogColor.y > FogColor.x));
        if (uw > 0.5 && N.y < -0.5) {
            _finalColor.rgb = mix(_finalColor.rgb, vec3(0.1, 0.45, 0.9), 0.85);
            _finalColor.rgb += fresnel(f0, NdotV) * 0.5 * (1.0 - malamd);

            //transparansi air
            _finalColor.a = lenWp / 15.0 + wave * 0.05;
            _finalColor.a *= 1.5;

            //snell window
            vec3 snellAir = _finalColor.rgb;
            float snellDay = smoothstep(0.05, 0.4, wk);
            vec3 snellV = normalize(wp);
            float snellCos = snellV.y + wave * SNELL_DISTORSI;
            float snellWin = smoothstep(SNELL_SUDUT - SNELL_LEMBUT, SNELL_SUDUT + SNELL_LEMBUT, snellCos);
            float snellRim = smoothstep(SNELL_LEMBUT * 3.0, 0.0, abs(snellCos - SNELL_SUDUT));

            //langit jendela
            float sinW = sqrt(max(1.0 - saturate(snellCos) * saturate(snellCos), 0.0));
            float sinA = min(sinW * SNELL_INDEKS, 1.0);
            float cosA = sqrt(max(1.0 - sinA * sinA, 0.0));
            float sinAD = clamp(sinA + wave * SNELL_DISTORSI, 0.0, 1.0);
            vec2 hdir = normalize(snellV.xz + vec2(0.0001, 0.0001));
            vec3 dA = vec3(hdir.x * sinA, cosA, hdir.y * sinA);
            float sGrad = clamp(pow(sinAD, SNELL_PUSAT) * 1.6, 0.1, 1.0);
            vec3 cPagiS = mix(vec3(0.1, 0.5, 1.0) * 0.7, mix(vec3(0.1, 0.5, 1.0) * 0.7, vec3_splat(1.0), SNELL_PUTIH), sGrad);
            vec3 cSoreS = mix(vec3(0.1, 0.5, 1.0) * 0.2, vec3(1.0, 0.8, 0.1), sGrad);
            vec3 cMalamS = mix(vec3_splat(0.1), vec3(0.1, 0.5, 1.0) * SNELL_MALAM_BIRU, sGrad);
            float sDay = smoothstep(0.15, 0.7, wk);
            vec3 langitS = mix(cMalamS, cPagiS, sDay);
            langitS = mix(langitS, cSoreS, saturate(sored * 2.0) * 0.7);

            //awan jendela
            vec2 cBase = dA.xz / max(dA.y, 0.02) + vec2_splat(wave * SNELL_DISTORSI);
            float cShd = 0.0;
            float cMm = 1.0;
            float cSc = 1.4;
            for (int i = 0; i < 12; i++) {
                cSc /= 1.009;
                cMm *= 0.96;
                float cAw = step(0.88, hashLangit(floor(cBase * cSc + vec2_splat(tm * 0.04))));
                cShd = mix(cShd, cMm, cAw);
            }
            float cMask = cShd * smoothstep(0.0, 0.12, dA.y) * sDay;
            langitS = mix(langitS, vec3_splat(1.0), cMask) * SNELL_LANGIT;
            langitS = mix(langitS, snellAir * (0.3 + 0.7 * snellDay), smoothstep(SNELL_TEPI_MULAI, 1.0, sinAD) * mix(SNELL_TEPI_MALAM, SNELL_TEPI, sDay));

            //bintang jendela
            vec2 stP = dA.xz * SNELL_BINTANG_SKALA;
            vec2 stI = floor(stP);
            float stH = hashLangit(stI + vec2(7.3, 7.3));
            vec2 stO = (vec2(hashLangit(stI + vec2(3.1, 3.1)), hashLangit(stI + vec2(8.7, 8.7))) - 0.5) * 0.5;
            float stD = smoothstep(0.12, 0.0, length(fract(stP) - 0.5 - stO));
            float stKedip = 0.5 + 0.5 * sin(tm * 2.5 + stH * 60.0);
            float stM = step(1.0 - SNELL_BINTANG_DENSITAS, stH) * stD * stKedip * (1.0 - snellDay) * smoothstep(0.05, 0.25, dA.y);
            langitS += vec3_splat(stM * SNELL_BINTANG_TERANG);

            vec3 snellTir = snellAir * SNELL_GELAP * (0.3 + 0.7 * snellDay);
            _finalColor.rgb = mix(snellTir, langitS, snellWin) + vec3(0.2, 0.45, 0.6) * snellRim * 0.4 * snellDay;
            _finalColor.a = mix(0.95, 1.0, snellWin);
        }
    }

    //glow
    {
        float oreGlow = oreGlowCode(oreTex.w);
        if (v_ditheringAndMaskTinting.y > 0.5) {
            oreGlow = 0.0;
        }
        if (oreGlow > 0.0) {
            float gf = glowFactor(lenKs, uv1.y, wk, malamd);
            _finalColor.rgb = mix(_finalColor.rgb, oreTex.rgb * GLOW_DASAR, saturate(oreGlow * gf * GLOW_TETAP));
            _finalColor.rgb += glowBloom(oreTex.rgb * oreGlow * GLOW_INTENSITY * gf);
        }
    }
#endif

    //fog
#if defined(OPAQUE)
    if (!(akhir)) {
        vec4 fgst = mix(_finalColor, mix(_finalColor, fogS * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(lenKs / 100.0)), (1.0 - hujand));
        _finalColor = fgst;
        _finalColor = mix(_finalColor, mix(_finalColor, fogS * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(lenKs / 20.0)), dudan);
        _finalColor = mix(_finalColor, mix(_finalColor, fogS * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(lenKs / FOG_HUJAN_JARAK)), hujand);
        if (udw) {
            _finalColor = mix(_finalColor, vec4(udwFogCol, 1.0), udwFog(lenKs));
        }
    }
#elif defined(ALPHA_TEST)
    _finalColor = mix(_finalColor, mix(_finalColor, fogS * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(length(-ks) / 100.0)), (1.0 - hujand));
    _finalColor = mix(_finalColor, mix(_finalColor, fogS * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(length(-ks) / FOG_HUJAN_JARAK)), hujand);
    if (udw) {
        _finalColor = mix(_finalColor, vec4(udwFogCol, 1.0), udwFog(lenKs));
    }
#elif defined(TRANSPARENT)
    _finalColor = mix(_finalColor, mix(_finalColor, fogS * sqrt(vec4(1.1, 1.1, 1.1, 1.0)), Apa * saturate(length(-wp) / 100.0)), (1.0 - hujand));
    _finalColor = mix(_finalColor, mix(_finalColor, fogS * sqrt(vec4(0.8, 0.8, 0.8, 1.0)), Apa * saturate(length(-wp) / FOG_HUJAN_JARAK)), hujand);
    if (udw) {
        _finalColor.rgb = mix(_finalColor.rgb, udwFogCol, udwFog(lenWp));
    }
#endif

#if defined(OPAQUE) || defined(ALPHA_TEST)
    //god rays
    if (udw && wk > 0.05) {
        float grJarak = smoothstep(1.0, GODRAY_DEKAT, lenWp) * (1.0 - smoothstep(GODRAY_JAUH_MIN, GODRAY_JAUH_MAX, lenWp));
        float grGate = grJarak * uv1.y * uwIn;
        if (grGate > 0.001) {
            float grDens = godRayFog(p, wp, gl_FragCoord.xy);
            float grBawah = smoothstep(-0.7, -0.2, N.y);
            float grA = grDens * grGate * wk * grBawah * GODRAY_POWER;
            _finalColor.rgb = mix(_finalColor.rgb, vec3(0.5, 1.05, 1.2), saturate(grA));
        }
    }
#endif

    //debug fog
    if (SUN_DEBUG > 1.5) {
        _finalColor.rgb = (SUN_DEBUG > 2.5) ? vec3_splat(FogColor.a) : FogColor.rgb;
    }

    diffuse = _finalColor;
#endif

    gl_FragColor = diffuse;
#endif
}
