package com.victoria.flashdrop

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class FlashdropApplication

fun main(args: Array<String>) {
	runApplication<FlashdropApplication>(*args)
}
