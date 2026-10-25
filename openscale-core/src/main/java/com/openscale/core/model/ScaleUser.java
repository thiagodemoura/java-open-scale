package com.openscale.core.model;

import java.util.Objects;

/**
 * Usuário da balança. Várias balanças exigem altura, idade e sexo antes de medir
 * (a Relaxmedic, por exemplo, só mostra o peso depois de receber esse perfil).
 *
 * @param id              identificador estável (usado em tópicos MQTT, nomes de arquivo etc.)
 * @param name            nome de exibição
 * @param heightCm        altura em centímetros
 * @param age             idade em anos
 * @param gender          sexo biológico usado nos cálculos de composição corporal
 * @param initialWeightKg peso de referência; só precisa ser plausível
 */
public record ScaleUser(String id, String name, int heightCm, int age, Gender gender, double initialWeightKg) {

    public ScaleUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gender, "gender");
        if (heightCm < 50 || heightCm > 250) throw new IllegalArgumentException("heightCm fora da faixa: " + heightCm);
        if (age < 1 || age > 120) throw new IllegalArgumentException("age fora da faixa: " + age);
        if (name == null || name.isBlank()) name = id;
        if (initialWeightKg <= 0) initialWeightKg = 60.0;
    }
}
