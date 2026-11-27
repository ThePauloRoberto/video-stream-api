#!/bin/sh
# Executado pelo LocalStack quando o serviço fica pronto.
set -eu

BUCKET="${S3_BUCKET:-videos}"

awslocal s3 mb "s3://${BUCKET}" 2>/dev/null || echo "Bucket ${BUCKET} já existe"
awslocal s3 ls "s3://${BUCKET}"

echo "LocalStack pronto: s3://${BUCKET}"
