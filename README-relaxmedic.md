# Balança Relaxmedic: conexão e dados de composição corporal

Este guia explica como conectar à balança Relaxmedic (app Fitdays, protocolo
icomon v107) via Bluetooth Low Energy e de onde vem cada indicador:

- peso
- IMC
- gordura (% e massa)
- gordura visceral
- músculo
- osso
- proteína
- água
- metabolismo basal (TMB)
- idade corporal
- massa livre de gordura (FFM)

O protocolo foi portado do `RelaxmedicHandler.kt` do
[openScale](https://github.com/oliexdev/openScale) (GPL-3.0).

---

## 1. O ponto mais importante: a balança só envia peso e impedâncias

A Relaxmedic **não transmite** gordura, músculo, água etc. Pela conexão BLE
chegam só dois tipos de dado:

| Dado bruto | Mensagem | Conteúdo |
|---|---|---|
| Peso | `0xA2` (ao vivo) e `0xA3` (final) | gramas |
| Impedâncias | `0xA3` | 10 valores em ohms, em dois grupos de 5 |

Todo o resto é **calculado no celular** a partir de três grupos de dados:

- peso e impedâncias, que vêm da balança;
- altura, idade e sexo, que vêm do perfil do usuário;
- um algoritmo de bioimpedância (BIA).

O app Fitdays faz esse cálculo com o algoritmo dele. O openScale usa a
biblioteca `Wla25BodyComposition`.

```
 Balança ──BLE──► peso (g) + 10 impedâncias (Ω)
                          │
 Perfil (altura, idade, sexo) ──►  algoritmo BIA  ──► gordura, água, músculo,
                                                      osso, visceral, proteína,
                                                      TMB, massa magra, idade corporal
```

Por isso os valores podem diferir entre o Fitdays e o openScale: cada um usa
a sua própria fórmula sobre os mesmos dados brutos.

---

## 2. Como conectar (passo a passo GATT)

### Pré-requisitos

- Um celular ou computador com Bluetooth 4.0 (BLE) ou superior.
- O **app Fitdays fechado**. Se ele estiver aberto, pode pegar a conexão antes
  do seu programa.
- O nome anunciado pela balança deve começar com `Relaxmedic`. Os nomes `SWAN`,
  `icomon` e `YG` usam o mesmo serviço `FFB0`, mas são outras famílias de
  protocolo.

### UUIDs

| UUID (16 bits) | Propriedade | Uso |
|---|---|---|
| `FFB0` | serviço | serviço principal |
| `FFB1` | write (com resposta) | perfil `0xBA` e ACK `0xB0` |
| `FFB2` | notify | peso ao vivo `0xA2` |
| `FFB3` | indicate | info `0xA1`, resultado `0xA3` e pedido de ACK `0xA0` |

UUID completo: `0000FFBx-0000-1000-8000-00805f9b34fb`.

### Sequência

1. **Escaneie** e filtre pelo nome `Relaxmedic…`. A balança só anuncia quando
   está ligada, então suba nela ou toque na plataforma.
2. **Conecte** via GATT e descubra os serviços.
3. **Ligue as notificações**, escrevendo no descritor CCCD `0x2902` de cada
   característica, uma operação por vez:
   - `FFB2`: notify (`01 00`);
   - `FFB3`: indicate (`02 00`).
4. **Envie o perfil `0xBA`** em `FFB1`, com resposta. A balança **só mostra o
   resultado depois de receber um perfil válido**.
5. **Receba `0xA2`** (peso oscilando) até a balança estabilizar.
6. **Receba `0xA3`**, o resultado final. Ele chega em 2 fragmentos e é repetido
   algumas vezes. Responda **`0xB0`** a cada repetição, publique só a primeira.
7. Responda `0xB0` também a qualquer `0xA0`.
8. Desconecte.

### Formato do frame (20 bytes, nos dois sentidos)

```
[0] seq   [1] len   [2] fragmento   [3..18] 16 bytes de payload   [19] checksum
```

- `len`: tamanho da mensagem completa **sem** o byte de comando.
- `fragmento`: 0 no primeiro frame, 1 no seguinte, e assim por diante. Cada
  frame contribui com os bytes `[3..18]`.
- `checksum`: `soma(bytes[3..18]) & 0x1F`. Frames com checksum errado são
  descartados **em silêncio**, nos dois sentidos.

### Perfil `0xBA` (celular → balança)

| Offset na mensagem | Tamanho | Campo |
|---|---|---|
| 0 | 1 | `0xBA` |
| 1 | 4 | hora Unix (s), big-endian |
| 5 | 2 | fuso em minutos; bit `0x8000` = negativo (Fortaleza: `0x80B4`) |
| 7 | 4 | id do usuário (`0`) |
| 11 | 1 | altura em cm |
| 12 | 2 | peso de referência × 100 |
| 14 | 1 | `(sexo << 7) \| idade`, com sexo 1 = masculino |
| 15 | 1 | `0x2F` (copiado de uma captura do app oficial) |
| 16 | 1 | `0x0F` (não cabe no frame e é descartado, como faz o app oficial) |

> **Atenção:** idade e sexo só são enviados no byte 14. Se esse byte estiver
> errado, a balança aceita sem reclamar e todos os indicadores calculados
> ficam errados.

### Resultado `0xA3` (balança → celular)

O payload é a mensagem sem o byte de comando.

| Offset | Tamanho | Campo |
|---|---|---|
| 0 | 4 | u32 big-endian; **os 18 bits baixos são o peso em gramas** |
| 4 | 1 | não usado |
| 5 | 2 × 10 | impedâncias, u16 big-endian, em **décimos de ohm** |

```java
int grams = u32be(payload, 0) & 0x3FFFF;
double kg = grams / 1000.0;
double imp0 = u16be(payload, 5) / 10.0;   // primeira impedância, em Ω
```

---

## 3. Usando este projeto

### Jeito mais simples: `BodyCompositionScale`

A classe `com.openscale.core.composition.BodyCompositionScale` faz tudo numa
chamada: **seleciona a balança, conecta, espera o resultado e devolve todos os
indicadores** num objeto `BodyComposition`.

```java
ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);

BodyCompositionScale scale = BodyCompositionScale.builder(user)
        .scale("Relaxmedic")                                    // seleciona a balança
        .calculator(new GenericBiaEstimator(                    // opcional: estimativas genéricas
                GenericBiaEstimator.ImpedanceSelector.index(1)))
        .onLiveWeight(kg -> System.out.println("… " + kg + " kg"))
        .build();

BodyComposition bc = scale.measure(transport, Duration.ofSeconds(60));   // ou measureAsync(...)

bc.weightKg();               // Peso
bc.bmi();                    // IMC
bc.bodyFatPercent();         // Gordura %
bc.fatMassKg();              // Massa gorda
bc.visceralFat();            // Gordura visceral
bc.muscleMassKg();           // Massa muscular
bc.skeletalMuscleKg();       // Músculo esquelético
bc.boneMassKg();             // Massa óssea
bc.proteinPercent();         // Proteína
bc.bodyWaterPercent();       // Água corporal
bc.extracellularWaterKg();   // Água extracelular (ECW)
bc.bmrKcal();                // Metabolismo basal
bc.metabolicAge();           // Idade corporal
bc.fatFreeMassKg();          // Massa livre de gordura (FFM)

System.out.println(bc.report());
```

Cada indicador vem como `OptionalDouble`. Com `bc.get(Metric.X)` você tem
também a **origem** (`SCALE`, `CALCULATED`, `ESTIMATED` ou `UNAVAILABLE`) e
uma observação: a fórmula usada ou o motivo de faltar. Exemplo de
`bc.report()` com o estimador genérico:

```
Peso                           78.45 kg         SCALE       informado por Relaxmedic
IMC                            25.60 kg/m²      CALCULATED  peso / altura²
Gordura corporal               25.03 %          ESTIMATED   (peso − FFM Sun 2003) / peso
Massa gorda                    19.64 kg         ESTIMATED   peso − FFM Sun 2003
Gordura visceral               —                UNAVAILABLE o algoritmo do fabricante não está disponível
Músculo esquelético            30.05 kg         ESTIMATED   Janssen 2000
Água corporal                  53.81 %          ESTIMATED   Sun 2003 (água total)
Água extracelular (ECW)        —                UNAVAILABLE exige bioimpedância multifrequência documentada
Metabolismo basal              1640.30 kcal/dia ESTIMATED   Katch-McArdle
Massa livre de gordura (FFM)   58.81 kg         ESTIMATED   Sun 2003
...
```

**Ordem de prioridade dos valores:**

1. o que a balança ou o handler publicou;
2. os calculadores que você adicionar, na ordem em que forem adicionados (por
   exemplo um port do `Wla25BodyComposition`, que deve vir **antes** do
   estimador genérico);
3. contas exatas (IMC, massa gorda, FFM).

Um valor da balança nunca é substituído por uma estimativa.

**Sobre o `GenericBiaEstimator`:** ele usa equações publicadas:

- Sun 2003 para água total e FFM;
- Janssen 2000 para músculo esquelético;
- Katch-McArdle para o metabolismo basal.

Essas equações foram validadas com resistência **mão-pé** em adultos. Ainda
não se sabe qual das 10 impedâncias da Relaxmedic equivale à resistência de
corpo inteiro, e por isso o índice é obrigatório. Para calibrar, compare com
o Fitdays em algumas pesagens e escolha o índice cujo resultado acompanha a
tendência dele. Os valores servem para **tendência**, não para comparação
absoluta.

### Usando a sessão diretamente

```java
ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);
ScaleHandlerProvider relaxmedic = ScaleHandlerRegistry.fromClasspath()
        .find("Relaxmedic").orElseThrow();

new ScaleSession(transport, relaxmedic, user, new ScaleSession.Listener() {
    public void onLiveWeight(double kg) { System.out.println("… " + kg + " kg"); }
    public void onMeasurement(ScaleMeasurement m) {
        System.out.println("Peso: " + m.weightKg());
        System.out.println("IMC:  " + m.get(MeasurementType.BMI).getAsDouble());
        System.out.println("Impedâncias: " + Arrays.toString(m.impedancesOhm()));
    }
}).start();
```

Aqui, `transport` é a sua implementação de `BleTransport` (veja o README
principal). Para testar sem a balança:

```bash
java -jar openscale-cli/target/openscale-cli.jar simulate --weight 78.4
java -jar openscale-cli/target/openscale-cli.jar replay --file docs/captura-exemplo.txt
```

---

## 4. Cada indicador: origem e situação neste projeto

Legenda da coluna **Situação**:

- ✅ disponível hoje;
- 🔧 precisa portar o algoritmo `Wla25BodyComposition` (seção 5);
- ➗ conta simples sobre outro valor, que fica disponível junto com o algoritmo;
- ❌ não disponível pelo protocolo aberto.

| Indicador | Origem | Como se obtém | Situação |
|---|---|---|---|
| **Peso** | balança | `0xA3`: `u32 & 0x3FFFF` gramas. A capacidade (ex.: 6 a 180 kg) é a da especificação do seu modelo; o campo de 18 bits comporta até 262 kg | ✅ |
| **IMC** | cálculo | `peso / (altura_m)²` | ✅ `withBmi()` |
| **Gordura corporal (%)** | algoritmo BIA | `result.fat` | 🔧 |
| **Massa gorda (kg)** | derivado | `peso × gordura% / 100` | ➗ |
| **Gordura visceral** | algoritmo BIA | `result.visceralFat` (índice, não kg) | 🔧 |
| **Massa muscular** | algoritmo BIA | `result.musclePercent` (%); em kg: `peso × % / 100` | 🔧 |
| **Músculo esquelético** | — | o caminho da Relaxmedic no openScale não produz esse valor | ❌ |
| **Massa óssea** | algoritmo BIA | `result.boneKg` | 🔧 |
| **Proteína (%)** | algoritmo BIA | `result.protein` | 🔧 |
| **Água corporal (%)** | algoritmo BIA | `result.water` | 🔧 |
| **Água extracelular (ECW)** | — | não é calculada pelo caminho da Relaxmedic no openScale; o Fitdays pode exibir, mas com algoritmo próprio, não documentado | ❌ |
| **TMB (metabolismo basal)** | algoritmo BIA | `result.bmrKcal` (kcal/dia) | 🔧 |
| **Idade corporal / metabólica** | algoritmo BIA | `Wla25BodyComposition.bodyAge(idade, gordura%, sexo)`; o openScale só aceita resultados entre 10 e 99 | 🔧 |
| **Massa livre de gordura (FFM)** | algoritmo BIA | `result.lbmKg` (o openScale chama de LBM); ou `peso − massa gorda` | 🔧 |

Os campos `MeasurementType` já existem para gordura, água, músculo, osso,
visceral, proteína, TMB, massa magra e idade metabólica. A
`BodyCompositionScale` já deriva massa gorda e FFM automaticamente.

Com o `GenericBiaEstimator` ligado, estes indicadores passam a ter
**estimativa** (`ESTIMATED`) mesmo sem o algoritmo do fabricante:

- gordura % e massa gorda;
- FFM;
- água corporal %;
- músculo esquelético;
- metabolismo basal.

Estes continuam dependendo do algoritmo do fabricante, porque não existe
equação validada para eles com esses dados:

- gordura visceral;
- massa óssea;
- proteína;
- idade corporal;
- massa muscular.

A água extracelular (ECW) continua indisponível em qualquer caso.

---

## 5. Como completar a composição corporal

1. Porte o arquivo do openScale
   `android_app/app/src/main/java/com/health/openscale/core/bluetooth/libs/Wla25BodyComposition.kt`
   para `com.openscale.scales.relaxmedic.Wla25BodyComposition`. Ele é GPL-3.0,
   a mesma licença deste projeto.
2. Chame o algoritmo no `RelaxmedicHandler`, antes do `publish`, como faz o
   handler original:

```java
var r = Wla25BodyComposition.compute(user.heightCm(), weightKg, imps);
ScaleMeasurement.Builder b = ScaleMeasurement.builder(r != null ? r.weightKg() : weightKg)
        .source("Relaxmedic").impedances(imps);
if (r != null) {
    b.set(MeasurementType.BODY_FAT, r.fat())
     .set(MeasurementType.WATER, r.water())
     .set(MeasurementType.MUSCLE, r.musclePercent())
     .set(MeasurementType.BONE, r.boneKg())
     .set(MeasurementType.VISCERAL_FAT, r.visceralFat())
     .set(MeasurementType.PROTEIN, r.protein())
     .set(MeasurementType.BMR, r.bmrKcal())
     .set(MeasurementType.LBM, r.lbmKg());
    int bodyAge = Wla25BodyComposition.bodyAge(user.age(), r.fat(), user.gender().isMale());
    if (bodyAge >= 10 && bodyAge <= 99) b.set(MeasurementType.METABOLIC_AGE, bodyAge);
}
// r == null: as impedâncias falharam na validação do algoritmo → publica só o peso
ctx().publish(b.build());
```

3. Pronto: CSV, MQTT/Home Assistant e Health Connect passam a receber os novos
   valores automaticamente. No Health Connect, gordura, osso, massa magra,
   água (convertida para kg) e TMB já estão mapeados.

---

## 6. Dicas para medições consistentes

A bioimpedância depende do contato dos pés com os eletrodos e do estado de
hidratação.

- **Pés descalços e limpos**, apoiados sobre os eletrodos. Com meia ou calçado,
  a balança mede só o peso.
- Meça **sempre no mesmo horário**, de preferência de manhã, depois de ir ao
  banheiro e antes de comer ou treinar.
- Evite medir logo após o banho, exercícios ou bebida alcoólica.
- Mantenha **altura, idade e sexo atualizados** no perfil.
- Se as impedâncias forem rejeitadas pelo algoritmo, o openScale publica **só
  o peso**. Repita a medição.

> Os valores de composição corporal são **estimativas** de um modelo
> estatístico, úteis para acompanhar tendências. Não substituem exames como
> DEXA ou bioimpedância clínica. A bioimpedância costuma ser contraindicada
> para quem usa marca-passo ou outro implante eletrônico, e na gestação;
> confira o manual da balança.
