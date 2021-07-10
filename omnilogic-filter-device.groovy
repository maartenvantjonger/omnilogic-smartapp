/**
 *  Omnilogic Filter
 *
 *  Copyright 2020 Maarten van Tjonger
 */
metadata {
  definition(
    name: 'Omnilogic Filter',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Switch Level'
    capability 'Actuator'
    attribute 'poolId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'level', 'number'
  }

  tiles {
    standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

    controlTile('level', 'device.level', 'slider', range: '(1..100)', height: 2, width: 2, canChangeIcon: true, decoration: 'flat', inactiveLabel: false) {
      state 'level', action: 'setLevel'
    }

    main('switch')
    details(['switch', 'level'])
  }
}

def parse(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Filter parse')
	parent.logDebug(statusXmlNode)

  def filterSpeed = statusXmlNode?.@filterSpeed?.text().toInteger()
  updateState(filterSpeed)
}

def updateState(speed) {
	parent.logDebug('Executing Omnilogic Filter updateState')

  def onOff = speed == 0 ? 'off' : 'on'
  sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
  sendEvent(name: 'level', value: speed, displayed: true)
}

def on() {
  parent.logDebug('Executing Omnilogic Filter on')
  setLevel(100)
}

def off() {
	parent.logDebug('Executing Omnilogic Filter off')
  setLevel(0)
}

def setLevel(level) {
  parent.logDebug('Executing Omnilogic Filter setLevel')
  setPumpSpeed(level)
}

def setPumpSpeed(speed) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('poolId') ?: 1],
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
