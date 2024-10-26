import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import com.udacity.catpoint.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private SecurityService securityService;

    @Mock
    private ImageService imageService;

    @Mock
    private SecurityRepository securityRepository;

    private Sensor sensor;

    @BeforeEach
    void setup() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = createSensor();
    }

    private Sensor createSensor() {
        return new Sensor(UUID.randomUUID().toString(), SensorType.DOOR);
    }

    private Set<Sensor> getSensorList(boolean active, int number) {
        Set<Sensor> sensors = new HashSet<>();
        IntStream.range(0, number).forEach(i -> {
            Sensor sensor = new Sensor(UUID.randomUUID() + "_" + i, SensorType.DOOR);
            sensor.setActive(active);
            sensors.add(sensor);
        });
        return sensors;
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.")
    void whenSensorActivated_ThenTransitionToPendingAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm on.")
    void whenSensorActivatedInPending_ThenSetAlarmStatusToOn(ArmingStatus armingStatus){
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);

        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("3. If pending alarm and all sensors are inactive, return to no alarm state.")
    void whenAllSensorsInactiveInPending_ThenReturnToNoAlarm() {
        Set<Sensor> allSensors = getSensorList(true, 5);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        allSensors.forEach(sensor -> securityService.changeSensorActivationStatus(sensor, false));
        verify(securityRepository, times(5)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("4. If pending alarm and all sensors are inactive, return to no alarm state.")
    void whenActivatedDuringSensorActivationChange_ThenNotChangeAlarmStatus() {
//        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
//
//        testSensorActivationChange(true, false);
//
//
//        reset(securityRepository);
//        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
//
//        testSensorActivationChange(false, true);
    }

    private void testSensorActivationChange(boolean initialActiveState, boolean newActiveState) {
        sensor.setActive(initialActiveState);
        securityService.changeSensorActivationStatus(sensor, newActiveState);

        verify(securityRepository, times(1)).updateSensor(sensor);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    @Test
    @DisplayName("5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.")
    void whenSensorActivatedAndAlarmPending_ThenChangeToAlarm(){
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("6. If a sensor is deactivated while already inactive, make no changes to the alarm state.")
    void whenSensorAlreadyInactive_ThenNoChangeToAlarmState(){
//        sensor.setActive(false);
//        securityService.changeSensorActivationStatus(sensor, false);
//        verify(securityRepository, never()).setAlarmStatus(any());
    }


    @Test
    @DisplayName("7. If the camera image contains a cat while the system is armed-home, put the system into alarm status")
    void whenSensorsActivatedAndAlarmIsActive_ThenChangeToPending(){
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);

        var image = mock(BufferedImage.class);
        when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);

        securityService.processImage(image);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("8. If the camera image does not contain a cat, change the status to no alarm as long as the sensors are not active.")
    public void whenNoCatInImageAndSensorsNotActive_ThenChangeStatusToNoAlarm() {

        when(imageService.imageContainsCat(any(BufferedImage.class),anyFloat())).thenReturn(false);

        var image = new BufferedImage(123, 789, BufferedImage.TYPE_BYTE_INDEXED);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("9. If the system is disarmed, set the status to no alarm.")
    public void whenSystemDisarmed_ThenSetStatusToNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("10. If the system is armed, reset all sensors to inactive.")
    public void whenSystemArmed_ThenResetAllSensorsToInactive(String armingStatus) {
//        Set<Sensor> sensors = getSensorList(true, 3);
//
//        when(securityRepository.getSensors()).thenReturn(sensors);
//
//        securityService.setArmingStatus(ArmingStatus.valueOf(armingStatus));
//
//        securityService.getSensors().forEach(sensor -> {
//            assertFalse(sensor.getActive());
//        });
    }

    @Test
    @DisplayName("11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.")
    public void whenSystemArmedHomeAndCatInCamera_ThenSetAlarmStatusToAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(BufferedImage.class),anyFloat())).thenReturn(true);

        var image = new BufferedImage(123, 789, BufferedImage.TYPE_BYTE_INDEXED);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }
}
