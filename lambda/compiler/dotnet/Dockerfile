# .NET 8 and .NET 10 SDKs, each with a prebuilt console template so `dotnet run`
# does not need NuGet (the Lambda has no internet).
# SDK tarballs are fetched on the host by deploy.sh into vendor/.
FROM public.ecr.aws/lambda/python:3.12

ENV DOTNET_CLI_TELEMETRY_OPTOUT=1 \
    DOTNET_NOLOGO=1 \
    DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

COPY vendor/dotnet-sdk-8-linux-x64.tar.gz /tmp/dotnet8.tgz
COPY vendor/dotnet-sdk-10-linux-x64.tar.gz /tmp/dotnet10.tgz

RUN dnf install -y tar gzip libicu findutils \
    && dnf clean all \
    && mkdir -p /opt/dotnet/8 /opt/dotnet/10 \
    && tar -xzf /tmp/dotnet8.tgz -C /opt/dotnet/8 \
    && tar -xzf /tmp/dotnet10.tgz -C /opt/dotnet/10 \
    && rm -f /tmp/dotnet8.tgz /tmp/dotnet10.tgz \
    && mkdir -p /opt/templates/dotnet8 /opt/templates/dotnet10 \
    && printf '%s\n' \
        '<Project Sdk="Microsoft.NET.Sdk">' \
        '  <PropertyGroup>' \
        '    <OutputType>Exe</OutputType>' \
        '    <TargetFramework>net8.0</TargetFramework>' \
        '    <ImplicitUsings>enable</ImplicitUsings>' \
        '    <Nullable>enable</Nullable>' \
        '  </PropertyGroup>' \
        '</Project>' > /opt/templates/dotnet8/App.csproj \
    && printf '%s\n' 'Console.WriteLine("ok");' > /opt/templates/dotnet8/Program.cs \
    && printf '%s\n' \
        '<Project Sdk="Microsoft.NET.Sdk">' \
        '  <PropertyGroup>' \
        '    <OutputType>Exe</OutputType>' \
        '    <TargetFramework>net10.0</TargetFramework>' \
        '    <ImplicitUsings>enable</ImplicitUsings>' \
        '    <Nullable>enable</Nullable>' \
        '  </PropertyGroup>' \
        '</Project>' > /opt/templates/dotnet10/App.csproj \
    && printf '%s\n' 'Console.WriteLine("ok");' > /opt/templates/dotnet10/Program.cs \
    && DOTNET_EnableWriteXorExecute=0 DOTNET_ROOT=/opt/dotnet/8 PATH="/opt/dotnet/8:$PATH" \
        dotnet build /opt/templates/dotnet8 -c Release \
    && DOTNET_EnableWriteXorExecute=0 DOTNET_ROOT=/opt/dotnet/10 PATH="/opt/dotnet/10:$PATH" \
        dotnet build /opt/templates/dotnet10 -c Release \
    && test -x /opt/dotnet/8/dotnet \
    && test -x /opt/dotnet/10/dotnet

# NuGet assets land under /root. Lambda does not run as root.
RUN chmod 755 /root \
    && chmod -R a+rX /root /opt/dotnet /opt/templates \
    && chmod -R a+rwX /root/.dotnet /root/.nuget /root/.local

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
