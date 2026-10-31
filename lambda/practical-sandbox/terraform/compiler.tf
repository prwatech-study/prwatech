# Compiler Lambdas live in ../compiler/terraform.
# This module's state is not in the repo. Applying it from a fresh directory would
# try to recreate the practice-sandbox VPC, buckets, and functions. The compiler
# stack looks up this VPC by name and only creates the new functions.
