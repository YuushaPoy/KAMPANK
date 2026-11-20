$input a_color0, a_position, a_texcoord0
#ifdef INSTANCING
    $input i_data1, i_data2, i_data3
#endif

$output v_color0, v_texcoord0, v_worldPos, fclr, p

#include <bgfx_shader.sh>

uniform vec4 SkyColor;
uniform vec4 FogColor;

void main() {
    vec3 worldPosition;
#ifdef INSTANCING
    mat4 model = mtxFromCols(i_data1, i_data2, i_data3, vec4(0.0, 0.0, 0.0, 1.0));
    worldPosition = instMul(model, vec4(a_position, 1.0)).xyz;
#else
    // kubah langit dilengkungkan ke bawah makin jauh dari pusat
    vec4 hp = vec4(a_position, 1.0);
    hp.y -= length(hp.xz) * 0.3;
    worldPosition = mul(u_model[0], hp).xyz;
#endif

    v_color0 = mix(SkyColor, FogColor, a_color0.x);
    v_texcoord0 = a_texcoord0;
    v_worldPos = worldPosition;
    fclr = FogColor;
    p = a_position;
    gl_Position = mul(u_viewProj, vec4(worldPosition, 1.0));
}
