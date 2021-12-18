/**
 *  OmniLogic VSP (Variable Speed Pump)
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition(
    name: "OmniLogic VSP",
    namespace: "maartenvantjonger",
    author: "Maarten van Tjonger",
    ocfDeviceType: "oic.d.watervalve"
  ) {
    capability "Switch"
    capability "Switch Level"
    capability "Actuator"
    capability "Refresh"

    attribute "bowId", "number"
    attribute "omnilogicId", "number"
    attribute "lastSpeed", "number"
    attribute "valvePosition", "number"
    attribute "whyFilterIsOn", "number"
    attribute "fpOverride ", "number"
    attribute "isSpillover", "number"
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
  sendEvent(name: "isSpillover", value: attributes["isSpillover"], displayed: true)
  sendEvent(name: "level", value: 0, displayed: true)
}

def refresh() {
  logMethod("refresh")
  parent.updateDeviceStatuses()
}

def parseStatus(deviceStatus, telemetryData) {
  logMethod("parseStatus", "Arguments", [deviceStatus])

  def valvePosition = deviceStatus?.@valvePosition?.text().toInteger()
  sendEvent(name: "valvePosition", value: valvePosition, displayed: true)

  def enabled = deviceStatus?.@filterState?.text() == "1" && (!getIsSpillover() || valvePosition == 3)
  def onOff = enabled ? "on" : "off"
  sendEvent(name: "switch", value: onOff, displayed: true)

  def level = deviceStatus?.@filterSpeed?.text().toInteger()
  sendEvent(name: "level", value: level, displayed: true)

  def lastSpeed = deviceStatus?.@lastSpeed?.text().toInteger()
  sendEvent(name: "lastSpeed", value: lastSpeed, displayed: true)

  def whyFilterIsOn = deviceStatus?.@whyFilterIsOn?.text().toInteger()
  sendEvent(name: "whyFilterIsOn", value: whyFilterIsOn, displayed: true)

  def fpOverride = deviceStatus?.@fpOverride?.text().toInteger()
  sendEvent(name: "fpOverride", value: fpOverride, displayed: true)
}

def on() {
  logMethod("on")
  def lastSpeed = device.currentValue("lastSpeed")?.toInteger()
  setPumpSpeed(lastSpeed > 0 ? lastSpeed : 100)
}

def off() {
  logMethod("off")
  setPumpSpeed(0)
}

def setLevel(level) {
  logMethod("setLevel", "Arguments", [level])
  setPumpSpeed(level)
}

def setPumpSpeed(speed) {
  logMethod("setPumpSpeed", "Arguments", [speed])

  if (getIsSpillover()) {
    setSpilloverPumpSpeed(speed)
  } else {
    setFilterPumpSpeed(speed)
  }
}

def setSpilloverPumpSpeed(speed) {
  logMethod("setSpilloverPumpSpeed", "Arguments", [speed])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "EquipmentID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "Speed", dataType: "int", value: speed],
    [name: "IsCountDownTimer", dataType: "bool", value: false],
    [name: "StartTimeHours", dataType: "int", value: 0],
    [name: "StartTimeMinutes", dataType: "int", value: 0],
    [name: "EndTimeHours", dataType: "int", value: 0],
    [name: "EndTimeMinutes", dataType: "int", value: 0],
    [name: "DaysActive", dataType: "int", value: 0],
    [name: "Recurring", dataType: "bool", value: false]
  ]

  parent.performApiRequest("SetUISpilloverCmd", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def onOff = speed == 0 ? "off" : "on"
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
      sendEvent(name: "level", value: speed, displayed: true, isStateChange: true)
    }
  }
}

def setFilterPumpSpeed(speed) {
  logMethod("setFilterPumpSpeed", "Arguments", [speed])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "EquipmentID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "IsOn", dataType: "int", value: speed],
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
      def onOff = speed == 0 ? "off" : "on"
      sendEvent(name: "switch", value: onOff, displayed: true, isStateChange: true)
      sendEvent(name: "level", value: speed, displayed: true, isStateChange: true)
    }
  }
}

def getIsSpillover() {
  return device.currentValue("isSpillover") == 1
}

def getPlatform() {
  physicalgraph?.device?.HubAction ? "SmartThings" : "Hubitat"
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
