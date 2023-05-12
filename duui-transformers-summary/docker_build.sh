#!/usr/bin/env bash


export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME=textimager-duui-transformers-summary
export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION=0.0.6
export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_LOG_LEVEL=DEBUG
export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_MODEL_CACHE_SIZE=10


docker build \
  --build-arg TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME \
  --build-arg TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION \
  --build-arg TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_LOG_LEVEL \
  -t ${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION} \
  -f src/main/docker/Dockerfile \
  .
