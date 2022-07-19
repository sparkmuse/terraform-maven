provider "aws" {
  region                      = "us-east-1"
  s3_use_path_style           = true
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  secret_key = "local"
  access_key = "local"
  token = "token"

  endpoints {
    s3  = var.url
  }
}

module "s3" {
  source = "../"
}
