/**
 *  Omnilogic Temperature Sensor
 *
 *  Copyright 2020 Maarten van Tjonger
 */
metadata {
  definition(
    name: 'Omnilogic Temperature Sensor',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Sensor'
    capability 'Temperature Measurement'
    attribute 'omnilogicId', 'string'
  }

  tiles {
    valueTile('temperature', 'device.temperature', width: 2, height: 2, canChangeIcon: true) {
      state('temperature', label: '${currentValue}')
    }

    main('temperature')
    details('temperature')
  }
}

def parse(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Temperature Sensor parse')
	parent.logDebug(statusXmlNode)

  def temperature = statusXmlNode?.@waterTemp?.text() ?: statusXmlNode?.@airTemp?.text()
  updateState(temperature)
}

def updateState(temperature) {
  sendEvent(name: 'temperature', value: temperature, unit: 'F', displayed: true)
}

