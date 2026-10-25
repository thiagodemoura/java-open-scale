# openScale Java (`com.openscale`)

Biblioteca Java pura (Maven, Java 25) para ler balanças Bluetooth e sincronizar
as medições, inspirada no [openScale](https://github.com/oliexdev/openScale)
e no openScale-sync.

| Módulo | Pacote | O que faz |
|---|---|---|
| `openscale-core` | `com.openscale.core` | Modelo de medição, transporte BLE abstrato, handlers plugáveis, sync, CSV |
| `openscale-scales` | `com.openscale.scales` | Handlers de balança. Hoje: **Relaxmedic** (protocolo icomon v107) |
| `openscale-sync-mqtt` | `com.openscale.sync.mqtt` | MQTT 5 com descoberta automática do **Home Assistant** |
| `openscale-sync-wger` | `com.openscale.sync.wger` | Peso no **wger** (wger.de ou instância própria) |
| `openscale-sync-healthconnect` | `com.openscale.sync.healthconnect` | Mapeia medições para registros do **Health Connect** |
| `openscale-cli` | `com.openscale.cli` | Linha de comando: simular, reproduzir capturas, lançar peso manual |

Balanças e destinos de sync são descobertos via `ServiceLoader`: basta colocar
o jar no classpath.

## Build

```bash
mvn verify                 # compila e roda os testes
mvn -pl openscale-cli -am package
java -jar openscale-cli/target/openscale-cli.jar list
```

## CLI

```bash
cp openscale.properties.example openscale.properties   # ajuste usuário e destinos

java -jar openscale-cli/target/openscale-cli.jar simulate --weight 78.4
java -jar openscale-cli/target/openscale-cli.jar manual   --weight 78.4
java -jar openscale-cli/target/openscale-cli.jar replay   --file docs/captura-exemplo.txt
```

`replay` lê uma notificação por linha (`<uuid curto> <hex>`), no formato que dá
para copiar do nRF Connect, e mostra o que o handler responderia (`→ FFB1 ...`).

## Uso como biblioteca

```java
ScaleUser user = new ScaleUser("thiago", "Thiago", 175, 40, Gender.MALE, 80);

Properties props = ...; // mesmo formato do openscale.properties
SyncDispatcher sync = new SyncDispatcher()
        .addAll(SyncTargetFactory.createEnabled(new SyncConfig(props)));

BleTransport transport = ...; // a sua implementação (Android, BlueZ...)
ScaleHandlerProvider relaxmedic = ScaleHandlerRegistry.fromClasspath()
        .find("Relaxmedic").orElseThrow();

new ScaleSession(transport, relaxmedic, user, new ScaleSession.Listener() {
    public void onLiveWeight(double kg) { System.out.println("… " + kg); }
    public void onMeasurement(ScaleMeasurement m) { System.out.println(m); }
}).withSync(sync).start();
```

### Transporte BLE

O núcleo não depende de nenhuma pilha Bluetooth. Implemente `BleTransport`
(conectar, ligar notify/indicate, escrever, avisar o listener). A única regra
é **serializar as operações GATT**, uma pendente por vez, como a fila do
`RelaxmedicScale.java` da versão Android anterior.

- **Android:** `BluetoothGatt`.
- **Linux:** BlueZ via D-Bus (`com.github.hypfvieh:bluez-dbus`).
- **Testes:** `SimulatedBleTransport`.

## Relaxmedic

Guia completo de conexão e dos indicadores (gordura, músculo, água, TMB,
idade corporal...): **[README-relaxmedic.md](README-relaxmedic.md)**.

| UUID | Uso |
|---|---|
| `FFB0` | serviço |
| `FFB1` | escrita: perfil `0xBA` e ACK `0xB0` |
| `FFB2` | notify: peso ao vivo `0xA2` |
| `FFB3` | indicate: resultado `0xA3` (2 fragmentos) |

A balança só mostra o peso depois de receber o perfil com altura, idade e sexo.
Se esses dados estiverem errados, ela aceita sem reclamar e calcula para a
pessoa errada. A composição corporal (gordura, água, músculo) não foi portada:
no openScale ela vem da biblioteca `Wla25BodyComposition`. Por isso a medição
traz peso, IMC e as 10 impedâncias brutas.

## Destinos de sincronização

Configurados em `openscale.properties` (ver `openscale.properties.example`).

- **CSV:** um arquivo por usuário, no estilo da exportação do openScale.
- **MQTT:** JSON retido em `openscale/<usuario>/measurement`. Com
  `homeAssistant=true`, os sensores aparecem sozinhos no Home Assistant.
- **wger:** `POST /api/v2/weightentry/`. No modo `AUTO` tenta data e hora; se
  o servidor recusar, usa só a data e atualiza a entrada do dia.
- **Health Connect:** a API só existe no Android, então o módulo entrega
  registros prontos (`HealthConnectRecord`) e o app grava. Fora do Android, o
  writer `log` só imprime.

Exemplo de writer no app Android (Kotlin, Jetpack Health Connect 1.1.x):

```kotlin
class AndroidHealthConnectWriter(private val client: HealthConnectClient) : HealthConnectWriter {
    override fun insert(records: List<HealthConnectRecord>) = runBlocking {
        client.insertRecords(records.mapNotNull { it.toAndroid() })
        Unit
    }
}

private fun meta(r: HealthConnectRecord) = Metadata.autoRecorded(
    device = Device(type = Device.TYPE_SCALE),
    clientRecordId = r.clientRecordId(),
    clientRecordVersion = r.time().toEpochMilli()
)

private fun HealthConnectRecord.toAndroid(): Record? = when (this) {
    is HealthConnectRecord.Weight -> WeightRecord(time(), zoneOffset(), Mass.kilograms(kilograms()), meta(this))
    is HealthConnectRecord.BodyFat -> BodyFatRecord(time(), zoneOffset(), Percentage(percent()), meta(this))
    is HealthConnectRecord.BoneMass -> BoneMassRecord(time(), zoneOffset(), Mass.kilograms(kilograms()), meta(this))
    is HealthConnectRecord.LeanBodyMass -> LeanBodyMassRecord(time(), zoneOffset(), Mass.kilograms(kilograms()), meta(this))
    is HealthConnectRecord.BodyWaterMass -> BodyWaterMassRecord(time(), zoneOffset(), Mass.kilograms(kilograms()), meta(this))
    is HealthConnectRecord.BasalMetabolicRate ->
        BasalMetabolicRateRecord(time(), zoneOffset(), Power.kilocaloriesPerDay(kcalPerDay()), meta(this))
    else -> null
}
```

O `clientRecordId` evita duplicar registros se a mesma medição for enviada de novo.

## Como adicionar uma balança

1. Crie `XxxHandler extends ScaleHandler` e `XxxProvider implements ScaleHandlerProvider`.
2. Registre o provider em
   `META-INF/services/com.openscale.core.scale.ScaleHandlerProvider`.
3. Para portar um handler do openScale (`core/bluetooth/scales/*.kt`), faça este mapeamento:
   - `setNotifyOn` → `ctx().enableNotifications`
   - `writeTo` → `ctx().write`
   - `publish` → `ctx().publish`
   - `userInfo` → `ctx().info`
4. Teste com `SimulatedBleTransport`, reproduzindo frames capturados.

## Como adicionar um destino de sync

Implemente `SyncTarget` e uma `SyncTargetFactory` com `id()`, e registre a
factory em `META-INF/services/com.openscale.core.sync.SyncTargetFactory`. As
propriedades `sync.<id>.*` passam a valer automaticamente.

## Licença

GPL-3.0-or-later. O protocolo da Relaxmedic é portado do openScale, que é GPL-3.0.
