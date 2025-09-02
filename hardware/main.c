/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : Main program body
  ******************************************************************************
  * @attention
  *
  * Copyright (c) 2024 STMicroelectronics.
  * All rights reserved.
  *
  * This software is licensed under terms that can be found in the LICENSE file
  * in the root directory of this software component.
  * If no LICENSE file comes with this software, it is provided AS-IS.
  *
  ******************************************************************************
  */
/* USER CODE END Header */
/* Includes ------------------------------------------------------------------*/
#include "main.h"
#include "adc.h"
#include "tim.h"
#include "usart.h"
#include "gpio.h"

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */
#include <string.h> // For strcmp and memset
/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */
// Servo motor PWM pulse width definitions (in microseconds)
// Based on 50Hz PWM (20ms period)
// -90 degrees (approx. 0.5ms pulse)
#define SERVO_MIN_PULSE_US  500
// 90 degrees (approx. 2.5ms pulse)
#define SERVO_MAX_PULSE_US  2000
// 0 degrees (approx. 1.5ms pulse)
#define SERVO_CENTER_PULSE_US 1000

// Convert pulse width to Duty Cycle based on PWM Counter Period (20000)
// (Pulse width / Period) * Counter Period
#define SERVO_MIN_DUTY_CYCLE  ((uint32_t)((float)SERVO_MIN_PULSE_US / 20000.0f * 20000.0f))
#define SERVO_MAX_DUTY_CYCLE  ((uint32_t)((float)SERVO_MAX_PULSE_US / 20000.0f * 20000.0f))
#define SERVO_CENTER_DUTY_CYCLE ((uint32_t)((float)SERVO_CENTER_PULSE_US / 20000.0f * 20000.0f))

// Light sensor ADC threshold values (example, adjust based on actual environment)
#define LIGHT_THRESHOLD_HIGH 2000 // If ADC value is higher, it's bright (LED OFF)
#define LIGHT_THRESHOLD_LOW  1000 // If ADC value is lower, it's dark (LED ON)

#define PWM_MIN  1000
#define PWM_MAX  2000
#define PWM_STEP 250

//#define TIM2 htim2
#define TIM2_CH TIM_CHANNEL_1
#define PWM_PERIOD         1000u

volatile uint32_t pwm_pulse_count = 0;

// 예: 30% → 60% → 90% 순환
static const uint16_t duty_table[] = { 300, 600, 900 };
static uint8_t duty_idx = 0;

//btn interrupt
#define USER_BTN_PORT      GPIOC
#define USER_BTN_PIN       GPIO_PIN_13
#define USER_BTN_EXTI_IRQn EXTI15_10_IRQn

// NUCLEO B1(파란 버튼): 풀다운, 눌렀을 때 High
#define NUCLEO_B1_ACTIVE_HIGH 1
volatile uint8_t user_btn_pressed = 0;

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/

/* USER CODE BEGIN PV */

uint8_t receive[] ="received";
uint8_t stop[] = "stop";
uint8_t newline[] = "\n";
uint8_t forward[] = "F";
uint8_t backward[] = "R";
uint8_t dd = 3;
uint8_t rr = 3;
uint16_t servo = 500;



// UART reception buffer
#define RX_BUFFER_SIZE 20
uint8_t Rx_data[RX_BUFFER_SIZE];
volatile uint8_t uart_rx_flag = 0; // UART reception complete flag
char received_string[RX_BUFFER_SIZE]; // For storing the received string

// DC motor control flag
volatile uint8_t dc_motor_stop_flag = 0;
uint32_t dc_motor_stop_start_time = 0; // DC motor stop start time

// Servo motor control variables
int8_t servo_direction = 1; // 1: increasing direction, -1: decreasing direction
uint32_t servo_current_duty = SERVO_MIN_DUTY_CYCLE; // Current servo duty cycle
uint32_t servo_last_move_time = 0; // Last time servo moved
#define SERVO_MOVE_INTERVAL_MS 50 // Interval to move servo (ms)
uint32_t period = 0;
uint32_t dclv = 0;

/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
/* USER CODE BEGIN PFP */

// DC motor control function prototypes (only forward and stop are used)
void DC_Motor_Forward(void);
void DC_Motor_Repeat(void);
void DC_Motor_Stop(void);

// Servo motor control function prototype

void Set_Servo_Angle(void);

//static void MX_GPIO_Init(void);
//static void MX_TIM4_Init(void);

/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */

#include "tim.h"
#include <stdint.h>

#define SERVO_MIN_US   1000   // 0도 근처(필요시 500~600으로 조정)
#define SERVO_MAX_US   2000   // 180도 근처(필요시 2400~2500으로 조정)
#define SERVO_PERIOD_US 20000 // 50Hz
#define TICK_US          1    // 타이머 1카운트 = 1us (PSC로 맞춤)



/* USER CODE END 0 */

/**
  * @brief  The application entry point.
  * @retval int
  */
int main(void)
{

  /* USER CODE BEGIN 1 */

  /* USER CODE END 1 */

  /* MCU Configuration--------------------------------------------------------*/

  /* Reset of all peripherals, Initializes the Flash interface and the Systick. */
  HAL_Init();

  /* USER CODE BEGIN Init */

  /* USER CODE END Init */

  /* Configure the system clock */
  SystemClock_Config();

  /* USER CODE BEGIN SysInit */

  /* USER CODE END SysInit */

  /* Initialize all configured peripherals */
  MX_GPIO_Init();
  MX_USART2_UART_Init();
  MX_ADC1_Init();
  MX_TIM2_Init();
  MX_USART6_UART_Init();
  MX_TIM4_Init();
  /* USER CODE BEGIN 2 */

  // Start servo motor PWM
  HAL_TIM_PWM_Start(&htim2, TIM_CHANNEL_1);

  //dc pwm
  HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_3);
  HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_4);
  period = __HAL_TIM_GET_AUTORELOAD(&htim4);

  // Set initial servo angle (0 degrees)
  __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_1, SERVO_CENTER_DUTY_CYCLE);

  // Start ADC conversion
  HAL_ADC_Start(&hadc1);

  // Enable UART reception interrupt
  // Set to receive 1 byte at a time. HAL_UART_RxCpltCallback will be called upon reception.
  HAL_UART_Receive_IT(&huart6, Rx_data, 1);

  // Initial DC motor state: move forward immediately upon start
  DC_Motor_Forward();

//  HAL_TIM_PWM_Start_IT(&TIM2, TIM2_CH);
  TIM2->CCR1 = 500;
  //btn interrupt
  /* LED: PA5 출력 */
  __HAL_RCC_GPIOA_CLK_ENABLE();
  GPIO_InitTypeDef GPIO_InitStruct = {0};
  GPIO_InitStruct.Pin   = GPIO_PIN_5;                  // LD2 (NUCLEO)
  GPIO_InitStruct.Mode  = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull  = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);
  HAL_GPIO_WritePin(GPIOA, GPIO_PIN_5, GPIO_PIN_RESET); // 시작 시 LED OFF

  /* 버튼: PC13 EXTI */
  __HAL_RCC_GPIOC_CLK_ENABLE();
  GPIO_InitStruct.Pin  = USER_BTN_PIN;
  #if NUCLEO_B1_ACTIVE_HIGH
  GPIO_InitStruct.Mode = GPIO_MODE_IT_RISING;          // 눌렀을 때 High
  GPIO_InitStruct.Pull = GPIO_PULLDOWN;
  #else
  GPIO_InitStruct.Mode = GPIO_MODE_IT_FALLING;         // 눌렀을 때 Low
  GPIO_InitStruct.Pull = GPIO_PULLUP;
  #endif
  HAL_GPIO_Init(USER_BTN_PORT, &GPIO_InitStruct);

  /* NVIC */
  HAL_NVIC_SetPriority(USER_BTN_EXTI_IRQn, 5, 0);
  HAL_NVIC_EnableIRQ(USER_BTN_EXTI_IRQn);

  HAL_UART_Transmit(&huart6, (uint8_t*)"BOOT\r\n", 6, 100);
  /* USER CODE END 2 */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1)
  {

	   Set_Servo_Angle();
    /* USER CODE END WHILE */

    /* USER CODE BEGIN 3 */
     LED();
    // 1. UART communication handling (receive 'warning' from Jetson Nano)
    if (uart_rx_flag == 1)
    {
       HAL_GPIO_WritePin(GPIOA, GPIO_PIN_5, GPIO_PIN_SET);
       HAL_Delay(10);
       HAL_GPIO_WritePin(GPIOA, GPIO_PIN_5, GPIO_PIN_RESET);
       HAL_Delay(10);

       HAL_UART_Transmit(&huart6, &receive, 8, 1000);
        // Check if the received string is 'warning'

        if (strcmp(received_string, "1\n") == 0)
        {
           HAL_UART_Transmit(&huart6, &stop, 4, 1000);
           HAL_UART_Transmit(&huart6, &newline, 1, 1000);
           DC_Motor_Stop();
           dd=0;
           rr=0;
           HAL_Delay(10000);
        }
        else if (strcmp(received_string, "forward\n") == 0)
        {
           HAL_UART_Transmit(&huart6, &forward, 1, 1000);
           HAL_UART_Transmit(&huart6, &newline, 1, 1000);
           DC_Motor_Forward();

        }
        else if (strcmp(received_string, "backward\n") == 0)
        {
           HAL_UART_Transmit(&huart6, &backward, 1, 1000);
           HAL_UART_Transmit(&huart6, &newline, 1, 1000);
           DC_Motor_Forward();
        }
        else if (strcmp(received_string, "w\n") == 0)
        {
           if(rr==0 && dd<3) dd++;
           else if (rr>0)rr--;
           if (dd==1) dd++;
           HAL_UART_Transmit(&huart6, &forward, 1, 1000);
           HAL_UART_Transmit(&huart6, &newline, 1, 1000);
           DC_Motor_Forward();

        }
        else if (strcmp(received_string, "s\n") == 0)
        {
           if(dd==0 && rr<3) rr++;
           else if (dd>0) dd--;
           if (dd==1) rr++;
           HAL_UART_Transmit(&huart6, &backward, 1, 1000);
           HAL_UART_Transmit(&huart6, &newline, 1, 1000);
           DC_Motor_Forward();
        }
        else if (strcmp(received_string, "a\n") == 0 && servo>=550)
        {
        	servo-=100;
        	TIM2->CCR2=servo;
        }
        else if (strcmp(received_string, "d\n") == 0 && servo<=2450)
        {
        	servo+=100;
        	TIM2->CCR2=servo;
        }
        else if (strcmp(received_string, "asdf\n") == 0)
        {
           HAL_UART_Transmit(&huart6, &backward, 1, 1000);
           HAL_UART_Transmit(&huart6, &newline, 1, 1000);
           DC_Motor_Repeat();
        }



        // Re-enable interrupt for next reception

        uart_rx_flag = 0; // Reset flag
        memset(received_string, 0, sizeof(received_string)); // Clear reception buffer
    }

    // Short delay (adjust as needed)
    HAL_Delay(10);

  }

  /* USER CODE END 3 */
}

/**
  * @brief System Clock Configuration
  * @retval None
  */
void SystemClock_Config(void)
{
  RCC_OscInitTypeDef RCC_OscInitStruct = {0};
  RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

  /** Configure the main internal regulator output voltage
  */
  __HAL_RCC_PWR_CLK_ENABLE();
  __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE2);

  /** Initializes the RCC Oscillators according to the specified parameters
  * in the RCC_OscInitTypeDef structure.
  */
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSE;
  RCC_OscInitStruct.HSEState = RCC_HSE_ON;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
  RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSE;
  RCC_OscInitStruct.PLL.PLLM = 4;
  RCC_OscInitStruct.PLL.PLLN = 84;
  RCC_OscInitStruct.PLL.PLLP = RCC_PLLP_DIV2;
  RCC_OscInitStruct.PLL.PLLQ = 7;
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
  {
    Error_Handler();
  }

  /** Initializes the CPU, AHB and APB buses clocks
  */
  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;
  RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;

  if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_2) != HAL_OK)
  {
    Error_Handler();
  }
}

/* USER CODE BEGIN 4 */

void LED(){
      HAL_ADC_Start(&hadc1);
      uint32_t adc_val = HAL_ADC_GetValue(&hadc1);
      //LED켜기
      if (adc_val < 1450){
         HAL_GPIO_WritePin(GPIOC, GPIO_PIN_0, GPIO_PIN_RESET);
      }
      else if(adc_val > 1550){
         HAL_GPIO_WritePin(GPIOC, GPIO_PIN_0, GPIO_PIN_SET);
      }
      else
         HAL_GPIO_WritePin(GPIOC, GPIO_PIN_0, GPIO_PIN_SET);
}

// DC motor control functions
void DC_Motor_Forward(void) {
     HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_3);
     HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_4);
   __HAL_TIM_SET_COMPARE(&htim4, TIM_CHANNEL_3, period*dd/3);
   __HAL_TIM_SET_COMPARE(&htim4, TIM_CHANNEL_4, period*rr/3);
}

void DC_Motor_Repeat(void) {
    if (dclv<1500){
  	  dd=0;
  	  rr=2;
  	  dclv++;
  	DC_Motor_Forward();
    }
    else if(dclv<3000){
  	   dd=2;
  	   rr=0;
  	   dclv++;
  	   DC_Motor_Forward();
    }
    else if(dclv==3000){
  	   dd=0;
  	   rr=0;
  	   dclv=0;
  	 DC_Motor_Forward();
    }
}

void DC_Motor_Stop(void) {
   HAL_TIM_PWM_Stop(&htim4, TIM_CHANNEL_3);
   HAL_TIM_PWM_Stop(&htim4, TIM_CHANNEL_4);
}

void EXTI15_10_IRQHandler(void)
{
    HAL_GPIO_EXTI_IRQHandler(USER_BTN_PIN);
}

void HAL_GPIO_EXTI_Callback(uint16_t GPIO_Pin)
{
    if (GPIO_Pin == USER_BTN_PIN) {
        // 디바운스가 필요하면 아래 2~3줄을 추가하세요 (HAL_GetTick() 활용)
        HAL_GPIO_WritePin(GPIOA, GPIO_PIN_5, GPIO_PIN_SET);   // LED 켜기
        HAL_Delay(10);
        HAL_GPIO_WritePin(GPIOA, GPIO_PIN_5, GPIO_PIN_RESET);   // LED 켜기
        HAL_Delay(10);
        user_btn_pressed = 1;

          if (dclv==0){
        	  dd=2;
        	  rr=0;
        	  dclv+=2000;
          }

           else if(dclv==2000) {
        	   dd=3;
        	   rr=0;
        	   dclv+=2000;
           }
           else if(dclv==4000){
        	   dd=0;
        	   rr=2;
        	   dclv+=2000;
           }
           else if(dclv==6000){
        	   dd=0;
        	   rr=3;
        	   dclv+=2000;
           }
           else if(dclv==8000){
        	   dd=0;
        	   rr=0;
        	   dclv=0;
           }
   //       if (dd==1) dd++;
    //      if(rr==1) rr++;
       }
    DC_Motor_Forward();

}

// Set servo motor angle
// angle: -90 to 90 degrees
void Set_Servo_Angle(void) {
	TIM2->CCR1=servo;
	  servo+=2;
	  if(servo==2500){
		  servo=500;
		  HAL_Delay(50);
	  }
}

// UART reception complete callback function (called when interrupt occurs)
void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)
{
    if (huart->Instance == USART6)
    {
        // Add the received byte to received_string
        // Prevent buffer overflow
        static uint8_t rx_index = 0;
        if (rx_index < RX_BUFFER_SIZE - 1) // Reserve space for null terminator
        {
            received_string[rx_index++] = Rx_data[0];
        }

        // If newline ('\n') or carriage return ('\r') is received, consider string reception complete
        if (Rx_data[0] == '\n' || Rx_data[0] == '\r')
        {
            received_string[rx_index] = '\0'; // Add null terminator
            uart_rx_flag = 1; // Set reception complete flag
            rx_index = 0; // Reset index for next reception
        }

        // Re-enable interrupt for next 1-byte reception
        HAL_UART_Receive_IT(&huart6, Rx_data, 1);
    }
}

void HAL_UART_ErrorCallback(UART_HandleTypeDef *huart)
{
    if (huart->Instance == USART6) {
        __HAL_UART_CLEAR_OREFLAG(huart);   // Overrun 등 클리어
        HAL_UART_Receive_IT(huart, Rx_data, 1); // 재-암
    }
}

/* USER CODE END 4 */

/**
  * @brief  This function is executed in case of error occurrence.
  * @retval None
  */
void Error_Handler(void)
{
  /* USER CODE BEGIN Error_Handler_Debug */
  /* User can add his own implementation to report the HAL error return state */
  __disable_irq();
  while (1)
  {
  }
  /* USER CODE END Error_Handler_Debug */
}
#ifdef USE_FULL_ASSERT
/**
  * @brief  Reports the name of the source file and the source line number
  *         where the assert_param error has occurred.
  * @param  file: pointer to the source file name
  * @param  line: assert_param error line source number
  * @retval None
  */
void assert_failed(uint8_t *file, uint32_t line)
{
  /* USER CODE BEGIN 6 */
  /* User can add his own implementation to report the file name and line number,
     ex: printf("Wrong parameters value: file %s on line %d\r\n", file, line) */
  /* USER CODE END 6 */
}
#endif /* USE_FULL_ASSERT */
