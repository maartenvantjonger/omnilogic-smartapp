/**
 *  Omnilogic Pump
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: 'Omnilogic Pump',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
  }

  tiles {
    standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

    main('switch')
    details(['switch'])
  }
}

def parse(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Pump parse')
	parent.logDebug(statusXmlNode)

  def pumpState = statusXmlNode?.@pumpState?.text().toInteger()
  updateState(pumpState == 1)
}

def updateState(on) {
  def onOff = on ? 'on' : 'off'
  sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
}

def on() {
  parent.logDebug('Executing Omnilogic Pump on')
  setPumpState(true)
}

def off() {
  parent.logDebug('Executing Omnilogic Pump off')
  setPumpState(false)
}

def setPumpState(isOn) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'EquipmentID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: isOn ? 100 : 0],
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
      updateState(isOn)
    }
  }
}
