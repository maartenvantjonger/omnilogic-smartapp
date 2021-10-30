/**
 *  OmniLogic Light
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: "OmniLogic Light",
    namespace: "maartenvantjonger",
    author: "Maarten van Tjonger"
  ) {
    capability "Switch"
    capability "Actuator"
    capability "Refresh"

    attribute "bowId", "number"
    attribute "omnilogicId", "number"

    command "setLightShow", ["NUMBER", "NUMBER", "NUMBER"]
  }

  tiles {
    standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true, decoration: "flat") {
      state("off", label: "${name}", action: "on")
      state("on", label: "${name}", action: "off")
    }

    main("switch")
    details(["switch"])
  }
}

def initialize(omnilogicId, attributes) {
  logMethod("initialize", "Arguments", [omnilogicId, attributes])
  sendEvent(name: "omnilogicId", value: omnilogicId, displayed: true)
  sendEvent(name: "bowId", value: attributes["bowId"], displayed: true)
}

def refresh() {
  logMethod("refresh")
  parent.updateDeviceStatuses()
}

def parseStatus(deviceStatus, telemetryData) {
  logMethod("parseStatus", "Arguments", [deviceStatus])

  def onOff = deviceStatus?.@lightState?.text()  == "1" ? "on" : "off"
  sendEvent(name: "switch", value: onOff, displayed: true)

  def currentShow = deviceStatus?.@currentShow?.text()
  if (currentShow != null) {
    sendEvent(name: "show", value: show, displayed: true)
  }
}

def on() {
  logMethod("on")
  setLightState(true)
}

def off() {
  logMethod("off")
  setLightState(false)
}

def setLightState(isOn) {
  logMethod("setLightState", "Arguments", [isOn])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "EquipmentID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "IsOn", dataType: "int", value: isOn ? 100 : 0],
    [name: "IsCountDownTimer", dataType: "bool", value: false],
    [name: "StartTimeHours", dataType: "int", value: 0],
    [name: "StartTimeMinutes", dataType: "int", value: 0],
    [name: "EndTimeHours", dataType: "int", value: 0],
    [name: "EndTimeMinutes", dataType: "int", value: 0],
    [name: "DaysActive", dataType: "int", value: 0],
    [name: "Recurring", dataType: "bool", value: false]
  ]

  parent.performApiRequest("SetUIEquipmentCmd", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def onOff = isOn ? "on" : "off"
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def setLightShow(show, speed, brightness) {
  logMethod("setLightShow", "Arguments", [show, speed, brightness])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "LightID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "Show", dataType: "int", value: show],
    [name: "Reserved", dataType: "byte", value: 0],
    [name: "IsCountDownTimer", dataType: "bool", value: false],
    [name: "StartTimeHours", dataType: "int", value: 0],
    [name: "StartTimeMinutes", dataType: "int", value: 0],
    [name: "EndTimeHours", dataType: "int", value: 0],
    [name: "EndTimeMinutes", dataType: "int", value: 0],
    [name: "DaysActive", dataType: "int", value: 0],
    [name: "Recurring", dataType: "bool", value: false],
    [name: "Speed", dataType: "byte", value: speed],
    [name: "Brightness", dataType: "byte", value: brightness],
  ]

  parent.performApiRequest("SetStandAloneLightShow", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      sendEvent(name: "switch", value: "on", displayed: true, isStateChange: true)
    }
  }
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
