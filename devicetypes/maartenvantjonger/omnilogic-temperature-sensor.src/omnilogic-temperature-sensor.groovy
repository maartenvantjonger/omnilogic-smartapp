/**
 *  Omnilogic Temperature Sensor
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition(
    name: 'Omnilogic Temperature Sensor',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Refresh'
    capability 'Sensor'
    capability 'Temperature Measurement'
    attribute 'omnilogicId', 'string'
    attribute 'lastTemperature', 'number'
    attribute 'lastTemperatureDate', 'string'
  }

  tiles {
    valueTile('lastTemperature', 'device.lastTemperature', width: 2, height: 2, canChangeIcon: true) {
      state('lastTemperature', label: '${currentValue}')
    }

    valueTile('temperature', 'device.temperature', width: 2, height: 2, canChangeIcon: true) {
      state('temperature', label: '${currentValue}')
    }

    main('lastTemperature')
    details(['lastTemperature', 'temperature'])
  }

  preferences {
    input(name: 'useLastTemperature', type: 'bool', title: 'Show last recorded actual temperature. Ignore -1 temperature updates.', defaultValue: true)
  }
}

def initialize(omnilogicId, attributes) {
	parent.logDebug('Executing Omnilogic Temperature Sensor initialize')

  settings.omnilogicId = omnilogicId
  settings.bowId = attributes['bowId']
  settings.sensorType = attributes['sensorType'] == 'SENSOR_WATER_TEMP' ? 'water' : 'air'

  sendEvent(name: 'unit', value: attributes['unit'] == 'UNITS_FAHRENHEIT' ? 'dF' : 'dC')
}

def refresh() {
	parent.logDebug('Executing Omnilogic Temperature Sensor refresh')
  parent.updateDeviceStatuses()
}

def parseStatus(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Temperature Sensor parseStatus')
	parent.logDebug(statusXmlNode)

  def temperature = settings.sensorType == 'water' ?
    statusXmlNode?.@waterTemp?.text() : statusXmlNode?.@airTemp?.text()
  updateState(temperature.toInteger())
}

def updateState(temperature) {
  parent.logDebug("Executing Omnilogic Temperature Sensor updateState temperature ${temperature}")

  if (temperature > -1) {
    sendEvent(name: 'temperature', value: temperature, unit: device.currentValue('unit'), displayed: true)
    sendEvent(name: 'lastTemperature', value: temperature, unit: device.currentValue('unit'), displayed: true)
    sendEvent(name: 'lastTemperatureDate', value:  new Date().format("yyyy-MM-dd'T'HH:mm:ss"), displayed: true)
  } else if (!useLastTemperature) {
    sendEvent(name: 'temperature', value: temperature, unit: device.currentValue('unit'), displayed: true)
  }
}

