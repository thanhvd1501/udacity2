package com.udacity.catpoint.security.service;


import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 * <p>
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private boolean catDetection = false;
    private final Set<StatusListener> statusListeners = new HashSet<>();

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * @param armingStatus ArmingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if(armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            if(armingStatus == ArmingStatus.ARMED_HOME && catDetection){
                setAlarmStatus(AlarmStatus.ALARM);
            }
            ConcurrentSkipListSet<Sensor> allSensors = new ConcurrentSkipListSet<>(securityRepository.getSensors());
            allSensors.forEach(sensor -> changeSensorActivationStatus(sensor, false));
        }
        securityRepository.setArmingStatus(armingStatus);
    }

    private void deactivateSensors() {
        var sensors = new ConcurrentSkipListSet<>(securityRepository.getSensors());
        sensors.forEach(sensor -> {
            changeSensorActivationStatus(sensor, false);
        });

    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        catDetection = cat;
        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if(!cat && checkIfAllSensorsInactive()){
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    private boolean checkIfAllSensorsInactive(){
        boolean allSensorsInactive = true;
        var allSensors = securityRepository.getSensors();
        for (Sensor sensor : allSensors) {
            if(sensor.getActive()){
                allSensorsInactive = false;
                break;
            }
        }
        return allSensorsInactive;
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     * @param statusListener StatusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * @param status AlarmStatus
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated(Sensor sensor) {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch(securityRepository.getAlarmStatus()) {
            case NO_ALARM : if(!sensor.getActive()){
                setAlarmStatus(AlarmStatus.PENDING_ALARM);
                break;
            }
            case PENDING_ALARM : setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated(Sensor sensor) {
        switch(securityRepository.getAlarmStatus()) {
            case PENDING_ALARM :
                boolean allSensorsInactive = true;
                for (Sensor sensor1 : securityRepository.getSensors()) {
                    if (sensor1.equals(sensor)){
                        continue;
                    }
                    if (sensor1.getActive()){
                        allSensorsInactive = false;
                        break;
                    }
                }
                if(allSensorsInactive) {
                    setAlarmStatus(AlarmStatus.NO_ALARM);
                }
                //case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     * @param sensor Sensor
     * @param active Boolean
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {

        if (active) {
            handleSensorActivated(sensor);
        } else if (sensor.getActive()) {
            handleSensorDeactivated(sensor);
        }

        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
        statusListeners.forEach(StatusListener::sensorStatusChanged);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     * @param currentCameraImage BufferedImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
