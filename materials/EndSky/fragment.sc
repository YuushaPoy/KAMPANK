$input v_color0, v_texcoord0, v_worldPos

#include <bgfx_shader.sh>

SAMPLER2D_AUTOREG(s_NoiseTexture); // textures/Noise/NoiseTexture.png (buffer: materials/EndSky/buffers/NoiseTexture.json)
uniform vec4 SkyColor;
uniform vec4 ViewPositionAndTime; // w = waktu (detik)

// ---------- Tweak di sini ----------
#define STAR_SCALE      80.0   // makin besar = bintang makin rapat/kecil
#define STAR_DENSITY    0.90   // makin kecil = bintang makin banyak
#define HORIZON_GLOW    0.85   // kuat semburat ungu di sisi
#define AURORA_POWER    1.3    // kuat aurora di atas
#define AURORA_SWIRL    2.2
#define AURORA_PULL     1.2
#define AURORA_RANGE    2.5
#define AURORA_DRIFT    0.02
// Black hole: devendrn (Simple blackhole), CC BY-SA 4.0 - dimodif: tepat di atas kepala, lebih kecil
#define NL_BH_COL_LOW   vec3(0.45, 0.10, 1.00) // ungu
#define NL_BH_COL_HIGH  vec3(2.40, 1.70, 3.00) // ungu muda keputihan
#define NL_BH_DIST      3.0    // makin besar = black hole makin kecil
#define NL_BH_SPEED     0.9    // kecepatan putaran black hole
#define NL_BH_ARMS      2.5    // seberapa jelas lengan spiral yang berputar
#define TWINKLE_SPEED   2.2    // kecepatan bintang berkelip
#define TWINKLE_AMOUNT  0.45   // 0 = tidak kelip, 1 = kelip penuh
#define NL_BH_GLOW      0.6    // kecerahan cincin
// Posisi black hole: tinggi 50 derajat dari horizon, arah -x lalu bergeser 20 derajat ke -z
// (E1 dan E2 = sumbu bidang singgung, harus ikut diganti kalau DIR diganti)
#define NL_BH_DIR       vec3(-0.6040, 0.7660, -0.2198)
#define NL_BH_E1        vec3(-0.3420, 0.0, 0.9397)
#define NL_BH_E2        vec3(0.7198, 0.6428, 0.2620)
// -----------------------------------

// ===== Awan Bicubic (port dari NL_SKY_CLOUDS), dilukis di kubah langit bawah =====
#define NL_SKY_CLOUDS_NOISE_CELLS 16.0    // sel voronoi tekstur (jangan ubah kalau tekstur tidak dibuat ulang)
#define NL_SKY_CLOUDS_LAYERS 7            // lapisan = volume & biaya (5 ringan ~ 9 berat). HP: 7
#define NL_SKY_CLOUDS_LOD_Y 0.05          // oktaf makin sedikit dekat horizon (0.04 detail ~ 0.08 cepat)
#define NL_SKY_CLOUDS_SCALE 0.34          // makin KECIL = awan makin BESAR (0.25 raksasa ~ 0.7 kecil)
#define NL_SKY_CLOUDS_DENSITY 1.15        // makin kecil = awan makin RAPAT (0.9 penuh ~ 1.8 jarang)
#define NL_SKY_CLOUDS_SPEED 1.0           // kecepatan angin
#define NL_SKY_CLOUDS_LAYER_SQUEEZE 0.05  // jarak antar lapisan (volume)
#define NL_SKY_CLOUDS_HORIZON_FADE 0.35   // awan memudar sampai ketinggian ini
#define NL_SKY_CLOUDS_EDGE_LO 0.10        // batas bawah tepi awan (makin besar = awan makin sedikit)
#define NL_SKY_CLOUDS_EDGE_HI 0.62        // batas atas tepi (makin dekat LO = tepi makin tegas)
#define CLOUD_COL      vec3(0.45, 0.32, 0.72)  // warna awan (1 warna saja, tanpa gradasi)
#define CLOUD_SKY_BASE vec3(0.13, 0.06, 0.26)  // warna dasar langit bawah, mengisi celah antar awan supaya tidak hitam

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float p8(float x) {
    x *= x;
    x *= x;
    return x * x;
}

vec4 renderBlackhole(vec3 vdir, float t) {
    t *= NL_BH_SPEED;

    vec3 vd = vdir - NL_BH_DIR; // pusat black hole
    float nl = sin(15.0 * vd.x + t) * sin(15.0 * vd.y - t) * sin(15.0 * vd.z + t);
    float a = atan2(dot(vd, NL_BH_E2) + 1e-6, dot(vd, NL_BH_E1)); // sudut di bidang singgung

    float d = NL_BH_DIST * length(vd + 0.003 * nl);
    float d0 = (0.6 - d) / 0.6;
    float dm0 = 1.0 - max(d0, 0.0);

    float gl = 1.0 - clamp(-0.3 * d0, 0.0, 1.0);
    float gla = p8(1.0 - min(abs(d0), 1.0));
    float gl8 = p8(gl);

    float h8 = p8(dm0);
    float h16 = h8 * h8;
    float hole = 0.9 * (h16 * h16) + 0.1 * dm0 * dm0 * dm0;
    float bh = (gla + 0.8 * gl8 + 0.2 * gl8 * gl8) * hole;

    float q = max(1.4 - d, 0.0);
    q *= q;
    float df = sin(3.0 * a - 4.0 * d + 24.0 * q * q + t);
    df *= 0.9 + 0.1 * sin(8.0 * a + d + 4.0 * t - 4.0 * df);
    float df2 = df * df;
    bh *= 1.0 + NL_BH_ARMS * df2 * df2 * hole * max(1.0 - bh, 0.0);

    vec3 col = bh * 4.0 * mix(NL_BH_COL_LOW, NL_BH_COL_HIGH, min(bh, 1.0));
    return vec4(col, hole);
}

float bsbeHash(float h) {
    return fract(sin(h) * 43758.5453);
}

float bsbeVoronoi(vec2 pos) {
    return texture2DLod(s_NoiseTexture, pos * (1.0 / NL_SKY_CLOUDS_NOISE_CELLS), 0.0).r;
}

float bsbeFbm(vec2 pos, float pDens, float fTime, int nOct) {
    float sum = 0.0;
    float sDens = 1.0;
    pos += fTime * 0.001 * NL_SKY_CLOUDS_SPEED;
    for (int i = 0; i < 4; i++) {
        if (i < nOct) {
            sum += bsbeVoronoi(pos) * sDens * pDens;
        } else {
            sum += 0.42 * sDens * pDens;
        }
        sDens *= 0.42;
        pos *= 2.3;
        pos += fTime * 0.05 * NL_SKY_CLOUDS_SPEED;
    }
    return clamp(1.0 - sum, 0.0, 1.0);
}

vec3 bsbeClouds(vec3 color, vec3 nPos, float fTime, vec3 cloudC, vec2 fragCoord) {
    vec2 cloudP = (nPos.xz / nPos.y) * NL_SKY_CLOUDS_SCALE;
    cloudP -= cloudP * bsbeHash(fragCoord.x + fragCoord.y * 57.0) * 0.05;

    float sDens = NL_SKY_CLOUDS_DENSITY;
    float fade = smoothstep(0.0, NL_SKY_CLOUDS_HORIZON_FADE, nPos.y);

    float lodY = nPos.y / NL_SKY_CLOUDS_LOD_Y;
    int nOct = int(step(1.0, lodY) + step(1.58, lodY) + step(2.5, lodY) + step(3.95, lodY));
    if (nOct > 0) {
        for (int i = 0; i < NL_SKY_CLOUDS_LAYERS; i++) {
            float m = bsbeFbm(cloudP, sDens, fTime, nOct);
            m = smoothstep(NL_SKY_CLOUDS_EDGE_LO, NL_SKY_CLOUDS_EDGE_HI, m);
            color = mix(color, cloudC, m * fade); // semua lapisan 1 warna
            sDens = max(sDens - 0.06, 0.6);
            cloudP -= cloudP * NL_SKY_CLOUDS_LAYER_SQUEEZE;
        }
    }
    return color;
}

vec3 bhWarp(vec3 d, float t) {
    vec3 vd = d - NL_BH_DIR;
    float k = exp(-length(vd) * AURORA_RANGE);
    float ang = AURORA_SWIRL * k;
    float cs = cos(ang);
    float sn = sin(ang);
    vec2 c = vec2(dot(vd, NL_BH_E1), dot(vd, NL_BH_E2));
    // rotasi 2D (setara mat2(cs, -sn, sn, cs) * c, ditulis manual agar aman di semua platform)
    c = vec2(cs * c.x + sn * c.y, -sn * c.x + cs * c.y);
    c *= 1.0 + AURORA_PULL * k * (1.0 + 0.12 * sin(t * 0.7));
    return normalize(NL_BH_DIR + c.x * NL_BH_E1 + c.y * NL_BH_E2 + dot(vd, NL_BH_DIR) * NL_BH_DIR);
}

void main() {
    vec3 d = normalize(v_worldPos);
    d.y = -d.y; // sumbu Y kubus langit kebalik, jadi dibalik di sini
    float time = ViewPositionAndTime.w;
    vec3 col = vec3(0.0, 0.0, 0.006);

    // ---- Semburat ungu di sisi: glow + sinar vertikal ----
    float g = 1.0 - abs(d.y);
    float g2 = g * g;
    float glow = g2 * g2;
    float up = smoothstep(-0.02, 0.08, d.y);
    vec2 az = normalize(d.xz + vec2(1e-5, 1e-5));
    float ray = 0.65 * vnoise(az * 7.0 + 2.0) + 0.35 * (0.5 + 0.5 * sin(dot(az, vec2(23.0, 17.0))));
    vec3 gc = mix(vec3(0.40, 0.08, 0.72), vec3(0.66, 0.40, 0.95), smoothstep(0.0, 0.6, d.y));
    float rays = g2 * g * (0.25 + 0.75 * ray) * up * (1.0 - smoothstep(0.55, 0.95, d.y));
    col += gc * (glow * 0.55 + rays * 0.8) * HORIZON_GLOW;
    col += vec3(0.10, 0.02, 0.18) * g2 * 0.30;

    // ---- Bintang (1 hash saja; sisanya diturunkan dari situ) ----
    vec3 p = d * STAR_SCALE;
    float rad = clamp(length(fwidth(p)) * 0.75, 0.04, 0.25); // derivatif di luar branch
    vec3 cell = floor(p);
    float r = hash13(cell);
    if (r >= STAR_DENSITY) {
        vec3 sp = fract(r * vec3(1271.0, 3117.0, 747.0)) * 0.6 + 0.2;
        float star = smoothstep(rad, 0.0, length(p - cell - sp));
        float bright = 0.45 + 0.55 * ((r - STAR_DENSITY) / (1.0 - STAR_DENSITY));
        bright *= 1.0 - TWINKLE_AMOUNT * (0.5 + 0.5 * sin(time * TWINKLE_SPEED + fract(r * 577.0) * 6.2832));
        vec3 starCol = mix(vec3(0.80, 0.85, 1.00), vec3(1.00, 0.82, 1.00), fract(r * 913.0));
        col += starCol * star * bright * 1.6 * (1.0 - glow * 0.8);
    }

    // ---- Aurora lembut di atas ----
    if (d.y > 0.05) {
        vec3 da = bhWarp(d, time);
        vec2 uv = da.xz / (max(da.y, 0.02) + 0.35);
        // rotasi 2D (setara mat2(0.825, -0.565, 0.565, 0.825) * uv)
        uv = vec2(0.825 * uv.x + 0.565 * uv.y, -0.565 * uv.x + 0.825 * uv.y);
        uv.x += time * AURORA_DRIFT;
        float bk = exp(-length(d - NL_BH_DIR) * AURORA_RANGE);
        float w = vnoise(uv * 0.9 + vec2(3.1, 1.7));
        float rb = 1.0 - abs(sin(uv.x * 1.7 + w * 5.0));
        float ribbon = rb * rb * rb;
        float streak = vnoise(vec2(uv.x * 6.0, uv.y * 0.6));
        float elev = smoothstep(0.10, 0.40, d.y) * (1.0 - 0.6 * smoothstep(0.65, 1.0, d.y));
        vec3 ac = mix(vec3(0.30, 0.08, 0.85), vec3(0.78, 0.38, 1.00), streak);
        col += ac * ribbon * (0.5 + 0.5 * streak) * elev * (AURORA_POWER * 0.8) * (1.0 + 0.8 * bk);
    }

    // ---- Awan Bicubic di bawah (berlapis, bergerak pelan) ----
    if (d.y < 0.0) {
        col += CLOUD_SKY_BASE * smoothstep(0.0, 0.12, -d.y);
        col = bsbeClouds(col, vec3(d.x, -d.y, d.z), time, CLOUD_COL, gl_FragCoord.xy);
    }

    // ---- Black hole di langit atas, miring ke -x (hanya dihitung di area kecil) ----
    if (dot(d, NL_BH_DIR) > 0.8) {
        vec4 bh = renderBlackhole(d, time);
        col *= bh.a;                 // serap cahaya di tengah
        col += bh.rgb * NL_BH_GLOW;  // cahaya cincin
    }

    // Tonemap lembut (exp2 lebih murah dari exp) + dither kecil anti-banding
    col = vec3_splat(1.0) - exp2(-2.164 * col);
    col += vec3_splat((hash12(gl_FragCoord.xy) - 0.5) / 255.0);

    gl_FragColor = vec4(max(col, vec3_splat(0.0)), SkyColor.a);
}
