# Jeelink Binding

This binding integrates JeeLink USB RF receivers into OpenHAB 2. 

## Introduction

Binding should be compatible with JeeLink USB receivers and connected LaCrosse temperature sensors as well as EC3000 sensors.

## Supported Things

This binding supports:

* JeeLink USB RF receivers as bridge
* LaCrosse Temeperature Sensors connected to the bridge (running the LaCrosseITPlusReader sketch)
* EC3000 Power Monitors connected to the bridge (running the ec3kSerial sketch)

## Prerequisites

The binding makes reads from the serial port and therefore needs openhab-transport-serial. Install the bundle in the **openhab2 console**
by typing: 
```
feature:install openhab-transport-serial
```

At least on linux the user running openhab needs to have the necessary rights to access the serial devices. He has to be added to the
group owning the device. Find out the group name in the **linux console** by typing
```
ls -al /dev/ttyUSB0
```
This should give output like
```
crw-rw---- 1 root dialout 188, 0 Okt  4 10:45 /dev/ttyUSB0
```
Now add the user to the group by typing:
```
sudo usermod -a -G groupname openhabuser
```
Make sure to replace _groupname_ and _openhabuser_ with the actual values. In this example group name is dialout. 

## Binding Configuration

Configuration of the binding is not needed. 

## Thing discovery

Only sensor discovery is supported, the thing for the USB receiver has to be created manually. Pay attention to use the correct serial port, as otherwise the binding may interfere with other bindings accessing serial ports.

Afterwards, discovery reads from the USB receiver to find out which sensors are currently connected. It then creates a thing for every sensor for which currently no other thing with the same sensor ID is registered with the bridge. 

## Thing configuration

#### JeeLink USB RF receivers

| Parameter         | Item Type    | Description
|-------------------|--------------|------------
| Port Name         | String       | Port to which the Jeelink is connected. See below
| Sketch Name       | String       | Currently only LaCrosseITPlusReader is supported
| Init Commands     | String       | A semicolon separated list of init commands that will be send to the Jeelink, e.g. "0a v" for disabling the LED.

The Port Name has to be of the form:

 * serial://_serialport_, e.g. serial://COM1 for windows or serial:///dev/ttyUSB0 for linux
 * tcp://_ip address_:_port_, e.g. tcp://127.0.0.1:6666

#### LaCrosse temperature sensors

| Parameter         | Item Type    | Description
|-------------------|--------------|------------
| Sensor ID         | Number       | The ID of the connected sensor
| Sensor Timeout    | Number       | The amount of time in seconds that should result in OFFLINE status when no readings have been received from the sensor
| Update Interval   | Number       | The update interval in seconds how often state updates are propagated
| Buffer Size       | Number       | The number of readings used for computing the rolling average
| Lower Temperature Limit | Decimal       | The lowest allowed valid temperature. Lower temperature readings will be ignored
| Upper Temperature Limit | Decimal       | The highest allowed valid temperature. Higher temperature readings will be ignored

#### EC3000 power monitors

| Parameter         | Item Type    | Description
|-------------------|--------------|------------
| Sensor ID         | Number       | The ID of the connected sensor
| Sensor Timeout    | Number       | The amount of time in seconds that should result in OFFLINE status when no readings have been received from the sensor
| Update Interval   | Number       | The update interval in seconds how often state updates are propagated
| Buffer Size       | Number       | The number of readings used for computing the rolling average


## Channels

#### JeeLink USB RF receivers 

Do not have any channels.

#### LaCrosse temperature sensors

| Channel Type ID         | Item Type    | Description
|-------------------------|--------------|------------
| sensorId                | -            | Only used for commanding
| sensorType              | Number       | The sensor type
| temperature             | Number       | Temperature reading
| humidity                | Number       | Humidity reading 
| batteryNew              | Contact      | Whether the battery is new (CLOSED) or not (OPEN)
| batteryLow              | Contact      | Whether the battery is low (CLOSED) or not (OPEN)

#### EC3000 power monitors

| Channel Type ID         | Item Type    | Description
|-------------------------|--------------|------------
| sensorId                | -            | Only used for commanding
| currentWatt             | Number       | Instantaneous power in Watt
| maxWatt                 | Number       | Maximum load power in Watt
| consumptionTotal        | Number       | Total energy  consumption 
| applianceTime           | Number       | Total electrical appliance operating time in hours
| sensorTime              | Number       | Total turn on time of power monitor in hours
| resets                  | Number       | Number of resets

## Commands

#### JeeLink USB RF receivers

Do not handle any commands.

#### LaCrosse temperature sensors and EC3000 power monitors

| Channel Type ID         | Command Type    | Command Syntax       | Description
|-------------------------|-----------------|----------------------|------------
| sensorId                | String          | SetSensorId _newID_  | Sets the sensor ID of the current sensor to the specified _newID_


## Items

A typical item configuration for a LaCrosse temperature sensor looks like this:
```
Number Humidty_WZ "Wohnzimmer" <humidity> (gLaCrosse, gLaCrosseHumChart) {channel="jeelink:lacrosse:1475927280866:humidity"}
Number Temperature_WZ "Wohnzimmer" <temperature> (gLaCrosse, gLaCrosseTempChart) {channel="jeelink:lacrosse:1475927280866:temperature"}
Contact Battery_Low_WZ "Batterie Leer WZ" (gLaCrosse) {channel="jeelink:lacrosse:1475927280866:batteryLow"}
```