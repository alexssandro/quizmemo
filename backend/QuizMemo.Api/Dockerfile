FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY QuizMemo.Api/QuizMemo.Api.csproj QuizMemo.Api/
RUN dotnet restore QuizMemo.Api/QuizMemo.Api.csproj
COPY . .
RUN dotnet publish QuizMemo.Api/QuizMemo.Api.csproj -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
COPY --from=build /app/publish .
EXPOSE 8080
ENTRYPOINT ["dotnet", "QuizMemo.Api.dll"]
