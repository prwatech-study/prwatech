# A dedicated VPC for this sandbox, deliberately separate from the account's shared default VPC
# (vpc-05a4858417d4d8b84 in this account — used broadly for unrelated things). Isolating the
# sandbox in its own VPC keeps its blast radius, teardown, and audit trail independent of
# whatever else lives in the default one; the Lambda needs nothing from that VPC anyway.

resource "aws_vpc" "sandbox" {
  cidr_block           = "10.90.0.0/24"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project}-vpc"
  }
}

# Two AZs purely so Lambda can spread ENIs across them — this VPC carries no other workload and
# has no HA requirement beyond that.
data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_subnet" "sandbox_private" {
  count             = 2
  vpc_id            = aws_vpc.sandbox.id
  cidr_block        = cidrsubnet(aws_vpc.sandbox.cidr_block, 1, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  # No map_public_ip_on_launch — these subnets never hand out a public IP.
  tags = {
    Name = "${var.project}-private-${count.index}"
  }
}

# No aws_internet_gateway resource and no aws_nat_gateway resource anywhere in this module —
# and this route table carries no 0.0.0.0/0 route. That absence, not a firewall rule, is the
# actual network-isolation guarantee the sandbox design relies on.
resource "aws_route_table" "sandbox_private" {
  vpc_id = aws_vpc.sandbox.id

  tags = {
    Name = "${var.project}-private-rt"
  }
}

resource "aws_route_table_association" "sandbox_private" {
  count          = length(aws_subnet.sandbox_private)
  subnet_id      = aws_subnet.sandbox_private[count.index].id
  route_table_id = aws_route_table.sandbox_private.id
}

# The only way out of this VPC at all: S3, over the private AWS network via this gateway
# endpoint — never through an internet gateway, because there isn't one.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.sandbox.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.sandbox_private.id]

  tags = {
    Name = "${var.project}-s3-endpoint"
  }
}

data "aws_prefix_list" "s3" {
  name = "com.amazonaws.${var.aws_region}.s3"
}

# No ingress rule at all (nothing calls into this function over the network — Lambda invokes it
# directly via the control plane, not through the VPC). Egress is restricted to the S3 prefix
# list only, so even code that tried anything else outbound has nowhere for the security group
# to route it.
resource "aws_security_group" "lambda_sandbox" {
  name        = "${var.project}-lambda-sg"
  description = "Sandbox Lambda: no ingress, egress restricted to the S3 prefix list only"
  vpc_id      = aws_vpc.sandbox.id

  egress {
    description     = "S3 only, via the gateway endpoint"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    prefix_list_ids = [data.aws_prefix_list.s3.id]
  }

  tags = {
    Name = "${var.project}-lambda-sg"
  }
}
