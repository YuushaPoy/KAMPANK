$input a_color0, a_position, a_texcoord0
#ifdef INSTANCING
    $input i_data1, i_data2, i_data3
#endif

$output v_color0, v_texcoord0, v_worldPos

#include <bgfx_shader.sh>

//matahari diputar 45 derajat (belah ketupat), bulan tidak
#define MATAHARI_PUTAR 1

void main() {
    vec3 lp = a_position;
#if MATAHARI_PUTAR
    //matahari: y lokal > 0, bulan: y lokal < 0
    if (lp.y > 0.0) {
        float c = 0.70710678;
        lp.xz = vec2((lp.x - lp.z) * c, (lp.x + lp.z) * c);
    }
#endif

    vec3 worldPosition;
#ifdef INSTANCING
    mat4 model = mtxFromCols(i_data1, i_data2, i_data3, vec4(0.0, 0.0, 0.0, 1.0));
    worldPosition = instMul(model, vec4(lp, 1.0)).xyz;
#else
    worldPosition = mul(u_model[0], vec4(lp, 1.0)).xyz;
#endif

    v_color0 = a_color0;
    v_texcoord0 = a_texcoord0;
    v_worldPos = worldPosition;
    gl_Position = mul(u_viewProj, vec4(worldPosition, 1.0));
}
