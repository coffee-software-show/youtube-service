#!/usr/bin/env bash
set -e
set -o pipefail

export APP_NAME=coffeesoftware-show-api
export SECRETS=${APP_NAME}-secrets
export SECRETS_FN=$HOME/${SECRETS}
export IMAGE_NAME=gcr.io/${PROJECT_ID}/${APP_NAME}
export RESERVED_IP_NAME=${APP_NAME}-ip
docker rmi -f $IMAGE_NAME
cd $ROOT_DIR
./gradlew bootBuildImage --imageName=$IMAGE_NAME
#./mvnw -DskipTests=true spring-javaformat:apply clean package spring-boot:build-image -Dspring-boot.build-image.imageName=$IMAGE_NAME
docker push $IMAGE_NAME
gcloud compute addresses list --format json | jq '.[].name' -r | grep $RESERVED_IP_NAME || gcloud compute addresses create $RESERVED_IP_NAME --global
touch $SECRETS_FN
echo writing to "$SECRETS_FN "
cat <<EOF >${SECRETS_FN}
COFFEE_SOFTWARE_SHOW_DB_HOST=${COFFEE_SOFTWARE_SHOW_DB_HOST}
COFFEE_SOFTWARE_SHOW_DB_NAME=${COFFEE_SOFTWARE_SHOW_DB_NAME}
COFFEE_SOFTWARE_SHOW_DB_PASSWORD=${COFFEE_SOFTWARE_SHOW_DB_PASSWORD}
COFFEE_SOFTWARE_SHOW_DB_URL=${COFFEE_SOFTWARE_SHOW_DB_URL}
COFFEE_SOFTWARE_SHOW_DB_USERNAME=${COFFEE_SOFTWARE_SHOW_DB_USERNAME}
COFFEE_SOFTWARE_SHOW_YOUTUBE_DATA_API_KEY=${COFFEE_SOFTWARE_SHOW_YOUTUBE_DATA_API_KEY}
EOF
kubectl delete secrets $SECRETS || echo "no secrets to delete."
kubectl create secret generic $SECRETS --from-env-file $SECRETS_FN
kubectl delete -f $ROOT_DIR/deploy/k8s/deployment.yaml || echo "couldn't delete the deployment as there was nothing deployed."
kubectl apply -f $ROOT_DIR/deploy/k8s
