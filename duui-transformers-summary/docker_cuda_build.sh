#!/usr/bin/env bash


export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME=textimager-duui-transformers-summary-cuda
export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION=0.0.7
export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_LOG_LEVEL=DEBUG
export TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_MODEL_CACHE_SIZE=10


docker build \
  --build-arg TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME \
  --build-arg TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION \
  --build-arg TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_LOG_LEVEL \
  -t ${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION} \
  -f src/main/docker/Dockerfile_cuda \
  .

#docker tag ${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION} docker.texttechnologylab.org/${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION}
#docker tag ${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION}  docker.texttechnologylab.org/${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:latest

#docker push docker.texttechnologylab.org/${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_VERSION}
#docker push docker.texttechnologylab.org/${TEXTIMAGER_DUUI_TRANSFORMERS_SUMMARY_ANNOTATOR_NAME}:latest