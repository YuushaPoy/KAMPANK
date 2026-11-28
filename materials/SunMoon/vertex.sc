$input a_color0, a_position, a_texcoord0
#ifdef INSTANCING
    $input i_data1, i_data2, i_data3
#endif

$output v_color0, v_texcoord0, v_worldPos

#include <bgfx_shader.sh>

//matahari dan bulan diputar 45 derajat
#define MATAHARI_PUTAR 1

//orbit
#define ORBIT_PUTAR 0.7853982

//jalur matahari/bulan sendiri: terbit/terbenam normal, siang hari naik ke atas kepala
#define SUN_JALUR_AKTIF 1
#define SUN_NAIK_MULAI 0.2     // ketinggian (sin elevasi) tempat benda mulai naik ke atas kepala (di bawah ini = gerak normal)
#define SUN_NAIK_SELESAI 0.55  // di atas ini benda sudah di puncak langit
#define SUN_PUNCAK 1.0         // 1.0 = tepat di atas kepala, lebih kecil = agak miring

vec3 orbitPutar(vec3 v) {
    float oSin = sin(ORBIT_PUTAR);
    float oCos = cos(ORBIT_PUTAR);
    v.xz = vec2(oCos * v.x + oSin * v.z, -oSin * v.x + oCos * v.z);
    return v;
}

void main() {
    vec3 lp = a_position;
#if MATAHARI_PUTAR
    float c = 0.70710678;
    lp.xz = vec2((lp.x - lp.z) * c, (lp.x + lp.z) * c);
#endif

    vec3 worldPosition;
#ifdef INSTANCING
    mat4 model = mtxFromCols(i_data1, i_data2, i_data3, vec4(0.0, 0.0, 0.0, 1.0));
    worldPosition = instMul(model, vec4(lp, 1.0)).xyz;
#else
    worldPosition = mul(u_model[0], vec4(lp, 1.0)).xyz;
#endif

    //orbit
    worldPosition = orbitPutar(worldPosition);

#if SUN_JALUR_AKTIF
    vec3 cpos;
#ifdef INSTANCING
    cpos = instMul(model, vec4(0.0, a_position.y, 0.0, 1.0)).xyz;
#else
    cpos = mul(u_model[0], vec4(0.0, a_position.y, 0.0, 1.0)).xyz;
#endif
    cpos = orbitPutar(cpos);
    vec3 cd = normalize(cpos);
    float eNaik = smoothstep(SUN_NAIK_MULAI, SUN_NAIK_SELESAI, cd.y);
    float e2 = mix(cd.y, SUN_PUNCAK, eNaik);
    vec2 hz = cd.xz;
    float hzLen = length(hz);
    hz = (hzLen > 0.0001) ? hz / hzLen : vec2(1.0, 0.0);
    float c2 = sqrt(max(1.0 - e2 * e2, 0.0));
    vec3 cd2 = vec3(hz.x * c2, e2, hz.y * c2);
    vec3 kx = cross(cd, cd2);
    float ks = length(kx);
    float kc = dot(cd, cd2);
    if (ks > 0.0001) {
        vec3 ka = kx / ks;
        worldPosition = worldPosition * kc + cross(ka, worldPosition) * ks + ka * dot(ka, worldPosition) * (1.0 - kc);
    }
#endif

    v_color0 = a_color0;
    v_texcoord0 = a_texcoord0;
    v_worldPos = worldPosition;
    gl_Position = mul(u_viewProj, vec4(worldPosition, 1.0));
}
