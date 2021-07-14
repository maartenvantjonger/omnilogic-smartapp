/**
 *  Omnilogic Light
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: 'Omnilogic Light',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Actuator'
    capability 'Refresh'
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

def initialize(omnilogicId, attributes) {
	parent.logDebug('Executing Omnilogic Light initialize')

  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
}

def refresh() {
	parent.logDebug('Executing Omnilogic Light refresh')
  parent.updateDeviceStatuses()
}

def parseStatus(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Light parseStatus')
	parent.logDebug(statusXmlNode)

  def lightState = statusXmlNode?.@lightState?.text()
  def currentShow = statusXmlNode?.@currentShow?.text()
  updateState(lightState == '1', currentShow)
}

def updateState(on, show) {
  def onOff = on ? 'on' : 'off'
  sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)

  if (show != null) {
    sendEvent(name: 'show', value: show, displayed: true)
  }
}

def on() {
  parent.logDebug('Executing Omnilogic Light on')
  setLightState(true)
}

def off() {
  parent.logDebug('Executing Omnilogic Light off')
  setLightState(false)
}

def setLightState(isOn) {
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
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      updateState(isOn)
    }
  }
}

/* TODO Add attributes
colors = {
    '1' : "Show-Voodoo Lounge",
    '2' : "Fixed-Deep Blue Sea",
    '3' : "Fixed-Royal Blue",
    '4' : "Fixed-Afternoon Skies",
    '5' : "Fixed-Aqua Green",
    '6' : "Fixed-Emerald",
    '7' : "Fixed-Cloud White",
    '8' : "Fixed-Warm Red",
    '9' : "Fixed-Flamingo",
    '10' : "Fixed-Vivid Violet",
    '11' : "Fixed-Sangria",
    '12' : "Show-Twilight",
    '13' : "Show-Tranquility",
    '14' : "Show-Gemstone",
    '15' : "Show-USA",
    '16' : "Show-Mardi Gras",
    '17' : "Show-Cool Cabaret"
}
*/
