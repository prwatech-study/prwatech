variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "compiler_image_tag" {
  type    = string
  default = "latest"
}

variable "prwatech_invoker_principal_arn" {
  type    = string
  default = "arn:aws:iam::733477849411:role/AI-Tutor-Role"
}
