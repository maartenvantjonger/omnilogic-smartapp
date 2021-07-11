/**
 *  Omnilogic Filter
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition(
    name: 'Omnilogic Filter',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Actuator'
    capability 'Fan Speed'
    capability 'Health Check'
    capability 'Refresh'
    capability 'Sensor'
    capability 'Configuration'

    command 'low'
    command 'medium'
    command 'high'
    command 'raiseFanSpeed'
    command 'lowerFanSpeed'
    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'fanSpeed', 'number'
  }

  tiles {
    standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

    controlTile('fanSpeed', 'device.fanSpeed', 'slider', range: '(1..100)', height: 2, width: 2, canChangeIcon: true, decoration: 'flat', inactiveLabel: false) {
      state 'fanSpeed', action: 'setFanSpeed'
    }

    main('switch')
    details(['switch', 'fanSpeed'])
  }
}

def parseStatus(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Filter parseStatus')
	parent.logDebug(statusXmlNode)

  def filterSpeed = statusXmlNode?.@filterSpeed?.text().toInteger()
  updateState(filterSpeed)
}

def updateState(speed) {
	parent.logDebug('Executing Omnilogic Filter updateState')

  def onOff = speed == 0 ? 'off' : 'on'
  sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
  sendEvent(name: 'fanSpeed', value: speed, displayed: true)
}

def on() {
  parent.logDebug('Executing Omnilogic Filter on')
  setPumpSpeed(100)
}

def off() {
	parent.logDebug('Executing Omnilogic Filter off')
  setPumpSpeed(0)
}

def high() {
  setPumpSpeed(100)
}

def medium() {
  setPumpSpeed(85)
}

def low() {
  setPumpSpeed(75)
}

def raiseFanSpeed() {
  setPumpSpeed(100)
}

def lowerFanSpeed() {
  setPumpSpeed(75)
}

def setSpeed(speed) {
  parent.logDebug("Executing Omnilogic Filter setSpeed ${speed}")

  switch (speed) {
    case 'low':
      setPumpSpeed(75)
      break
    case 'medium-low':
      setPumpSpeed(80)
      break
    case 'medium':
      setPumpSpeed(85)
      break
    case 'medium-high':
      setPumpSpeed(90)
      break
    case 'high':
      setPumpSpeed(100)
      break
    case 'on':
      setPumpSpeed(100)
      break
    case 'off':
      setPumpSpeed(0)
      break
    case 'auto':
      setPumpSpeed(100)
      break
  }
}

def setFanSpeed(speed) {
  parent.logDebug('Executing Omnilogic Filter fanSpeed')
  setPumpSpeed(speed * 25)
}

def setPumpSpeed(speed) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'EquipmentID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: speed],
    [name: 'IsCountDownTimer', dataType: 'bool', value: false],
    [name: 'StartTimeHours', dataType: 'int', value: 0],
    [name: 'StartTimeMinutes', dataType: 'int', value: 0],
    [name: 'EndTimeHours', dataType: 'int', value: 0],
    [name: 'EndTimeMinutes', dataType: 'int', value: 0],
    [name: 'DaysActive', dataType: 'int', value: 0],
    [name: 'Recurring', dataType: 'bool', value: false]
  ]

  parent.performApiRequest('SetUIEquipmentCmd', parameters) { response ->
    if (response) {
      updateState(speed)
    }
  }
}
