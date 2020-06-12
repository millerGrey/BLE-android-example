#include <Arduino.h>

/*
    Видео: https://www.youtube.com/watch?v=oCMOYS71NIU
    Основан на скетче-примере Нила Колбана для IDF:
    https://github.com/nkolban/esp32-snippets/blob/master/cpp_utils/tests/BLE%20Tests/SampleNotify.cpp
    Порт в ESP32-аддон для IDE Arduino - Эвандро Коперчини
   
    Создаем BLE-сервер, который после подключения клиента
    начнет отправлять ему периодические уведомляющие сообщения.
 
    Шаги создания BLE-сервера таковы:
     1. Создание BLE-сервера
     2. Создание BLE-сервиса
     3. Создание BLE-характеристики в BLE-сервисе
     4. Создание BLE-дескриптора в BLE-характеристике
     5. Запуск сервера
     6. Запуск рассылки оповещений (advertising)
   
    В результате устройство, выступающее сервером,
    запустит фоновую задачу по рассылке уведомляющих сообщений
    каждые несколько секунд.
*/
 
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
 
int sendString(uint8_t* str);
void cmdHandler(uint8_t* str);
int sendJournalString(uint8_t num);
int sendParamString(uint8_t num);

#define WAIT 0
#define SEND_J 1
#define SEND_P 2
#define JOURNAL_STRING_COUNT 10
#define PARAMETER_COUNT 10

const char CMD_GET[] = "get";

const char OK_MESSAGE[] = "OK";
const char END_OF_TRANSMIT[] = "";


const char ERROR_WRONG_DATA[] = "ER: Wrong data";
const char ERROR_NOT_SUPPORT_CMD[] = "ER: No support command";
const char ERROR_PROTOCOL[] = "ER: Protocol error!";

BLECharacteristic *pCharacteristic;
BLECharacteristic *pCharacteristic1;
bool deviceConnected = false;
bool printValue = false;
uint8_t value = 0;
 
void onWriteHandler(uint8_t* str);
char j[JOURNAL_STRING_COUNT][100] = {"journal string 1\r\n",
                    "journal string 2\r\n",
                    "journal string 3\r\n",
                    "journal string 4\r\n",
                    "journal string 5\r\n",
                    "journal string 6\r\n",
                    "journal string 7\r\n",
                    "journal string 8\r\n",
                    "journal string 9\r\n",
                    "journal string 10\r\n"};

uint8_t strNum=0;
// Сайт для генерирования UUID:
// https://www.uuidgenerator.net/
 
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_1_UUID "0000fe41-8e22-4541-9d4c-21edae82ed19" 

 
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
       Serial.println("device connect");
      deviceConnected = true;
    };
 
    void onDisconnect(BLEServer* pServer) {
      Serial.println("device disconnect");
      deviceConnected = false;
    }
};
class MyDescCallback: public BLEDescriptorCallbacks{
  void onWrite(BLEDescriptor* pDescriptor){
    Serial.println("descWrite");
  }
};

class MyCharCallbacks: public BLECharacteristicCallbacks {

  void onWrite(BLECharacteristic* pChar){
      if(!memcmp((const void*)(pChar->getUUID().toString().c_str()),(const void*)("0000fe41-8e22-4541-9d4c-21edae82ed19"), sizeof(CHARACTERISTIC_1_UUID)))
      {
        onWriteHandler((uint8_t*)pChar->getValue().c_str());
      }
  }
};
 
#define WAIT 0
#define SEND_J 1
#define SEND_P 2
void onWriteHandler(uint8_t* str){
  
  static uint8_t state;
	static uint8_t cnt;
  if(!memcmp(str, CMD_GET, strlen(CMD_GET)))
	{
		cnt=0;
		state = SEND_J;
		sendJournalString(cnt);
		cnt++;
		return;
	}

  else if(memcmp(str, OK_MESSAGE, 2)){
    sendString((uint8_t*)ERROR_NOT_SUPPORT_CMD);
    return;
  }

  switch(state)
  {
    case WAIT:

        break;
    case SEND_J:
      if(cnt >= JOURNAL_STRING_COUNT)
      {
      sendString((uint8_t*)END_OF_TRANSMIT);
        cnt=0;
        state = WAIT;
        break;
      }
      sendJournalString(cnt);
      cnt++;
    break;
  }

} 

int sendJournalString(uint8_t num){
	char str[100];
	if(num >= JOURNAL_STRING_COUNT){
		 memcpy(str,"",sizeof(""));
	}
	else{
		memcpy(str,j[num],sizeof(j[num]));
	}
	return sendString((uint8_t*)str);
}


int sendString(uint8_t* str)
{
        pCharacteristic->setValue((char*)str);
        pCharacteristic->notify();
	return 0;
}






void setup() {
  Serial.begin(115200);
 
  // создаем BLE-устройство:
  BLEDevice::init("MyESP32");

  // Создаем BLE-сервер:
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
 
  // Создаем BLE-сервис:
  BLEService *pService = pServer->createService(SERVICE_UUID);
 
  // Создаем BLE-характеристику:
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_1_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE  |
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_INDICATE
                    );

 
  // https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
  // создаем BLE-дескриптор:
  BLEDescriptor* desc = new BLE2902();
  desc->setCallbacks(new MyDescCallback);
  pCharacteristic->addDescriptor(desc);
  pCharacteristic->setCallbacks(new MyCharCallbacks);

  // запускаем сервис:
  pService->start();
 
  // запускаем оповещения (advertising):
  pServer->getAdvertising()->start();
  Serial.println("Waiting a client connection to notify...");  //  "Ждем подключения клиента, чтобы отправить ему уведомление..."
}
 
void loop() {
 
}