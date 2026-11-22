$input a_color0, a_position, a_texcoord0
#ifdef INSTANCING
    $input i_data1, i_data2, i_data3
#endif

$output v_color0, v_texcoord0, v_worldPos

#include <bgfx_shader.sh>

//matahari dan bulan diputar 45 derajat (belah ketupat)
#define MATAHARI_PUTAR 1

//arah terbit-tenggelam diputar mengelilingi sumbu vertikal (radian): 0.7853982 = 45 derajat
//positif: terbit di pojok (+x, -z), tenggelam di pojok (-x, +z). Balik tandanya untuk arah sebaliknya, 0.0 = mati
#define ORBIT_PUTAR 0.7853982

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
    float oSin = sin(ORBIT_PUTAR);
    float oCos = cos(ORBIT_PUTAR);
    worldPosition.xz = vec2(oCos * worldPosition.x + oSin * worldPosition.z, -oSin * worldPosition.x + oCos * worldPosition.z);

    v_color0 = a_color0;
    v_texcoord0 = a_texcoord0;
    v_worldPos = worldPosition;
    gl_Position = mul(u_viewProj, vec4(worldPosition, 1.0));
}
