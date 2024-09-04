FROM python:3.10.12

WORKDIR /usr/src/app

# install dependencies
RUN python3 -m venv env
RUN /bin/bash -c "source env/bin/activate"
RUN pip install --upgrade --root-user-action=ignore pip setuptools wheel
COPY ./requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --root-user-action=ignore -r ./requirements.txt
RUN python -m spacy download en_core_web_sm

# copy server files
COPY ./dkpro-core-types.xml ./dkpro-core-types.xml
COPY ./communication.lua ./communication.lua
COPY ./app.py ./app.py

# run server
ENTRYPOINT ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "9714"]
CMD ["--workers", "1"]
