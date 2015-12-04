#!/bin/sh

# set correct base dir for production and daemon operation
BASE_DIR="."	

# OpenCL device type to be used. Default is GPU.
# Available options: CPU, GPU, ACCELERATOR, DEFAULT, ALL
OPENCL_DEVICE="GPU"

# assemble options
FELIX_CACHE="$BASE_DIR/felix-cache"
LOG_OPTS="-Dtinylog.configuration=$BASE_DIR/conf/log.properties"
OPENCL_OPTS="-Docl.device.type=$OPENCL_DEVICE -Docl.profiling=no"
CONFIG_OPTS="-Dfelix.config.properties=file:$BASE_DIR/conf/config.properties -Dfelix.fileinstall.dir=$BASE_DIR/conf/fileinstall/"

# erase felix cache
rm -rf $FELIX_CACHE/*

# start LectureSight
java -Dlecturesight.basedir=$BASE_DIR $CONFIG_OPTS $LOG_OPTS $OPENCL_OPTS -jar $BASE_DIR/bin/felix.jar -b $BASE_DIR/bundles/system $FELIX_CACHE
