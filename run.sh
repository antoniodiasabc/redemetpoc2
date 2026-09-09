#!/bin/bash
#echo "🔄 Parando processos Java..."
#pkill -f java

echo "🔨 Compilando..."
javac OpmetSwingClient.java

echo "📦 Criando JAR..."
jar cfe OpmetSwingClient.jar OpmetSwingClient OpmetSwingClient.class

echo "🚀 Iniciando aplicação..."
java -jar OpmetSwingClient.jar &

sleep 2

echo "✅ Verificando processo:"
ps aux | grep java | grep -v grep
