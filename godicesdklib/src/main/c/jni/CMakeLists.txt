cmake_minimum_required(VERSION 3.4.1)

project(godicesdklib)

add_library(godicesdklib SHARED
			jni_def.c
			C:/Users/ADMIN/AndroidStudioProjects/LastDrop/godicesdklib/common/godiceapi.c)

target_include_directories(godicesdklib PRIVATE "C:/Users/ADMIN/AndroidStudioProjects/LastDrop/godicesdklib/common")
target_link_libraries(godicesdklib android log)
