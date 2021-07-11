/**
 *  Omnilogic Chlorinator
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: 'Omnilogic Chlorinator',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Switch Level'
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
	parent.logDebug('Executing Omnilogic Chlorinator parse')
	parent.logDebug(statusXmlNode)

  // TODO fix
  //def filterSpeed = statusXmlNode?.@filterSpeed?.text()
  //updateState(filterSpeed)
}

def updateState(level) {
	parent.logDebug('Executing updateState')

  def onOff = level == 0 ? 'off' : 'on'
  sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
  sendEvent(name: 'level', value: level, displayed: true)
}

def on() {
  parent.logDebug('Executing on')
  setLevel(100)
}

def off() {
	parent.logDebug('Executing off')
  setLevel(0)
}

def setLevel(level) {
  parent.logDebug('Executing setLevel')
  setChlorinatorLevel(level)
}

// TODO fix
def setChlorinatorLevel(level) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'EquipmentID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: level],
    [name: 'IsCountDownTimer', dataType: 'bool', value: false],
    [name: 'StartTimeHours', dataType: 'int', value: 0],
    [name: 'StartTimeMinutes', dataType: 'int', value: 0],
    [name: 'EndTimeHours', dataType: 'int', value: 0],
    [name: 'EndTimeMinutes', dataType: 'int', value: 0],
    [name: 'DaysActive', dataType: 'int', value: 0],
    [name: 'Recurring', dataType: 'bool', value: false]
  ]

  parent.performApiRequest('SetCHLOREnable', parameters) { response ->
    if (response) {
      updateState(level)
    }
  }
}
