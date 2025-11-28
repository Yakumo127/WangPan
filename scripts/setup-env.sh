#!/bin/bash

# 容器内环境配置脚本
echo "============================================"
echo "  配置开发环境"
echo "============================================"

# 设置代理（如果需要）
if [[ "$1" == "proxy" ]]; then
    echo "配置代理设置..."
    export HTTP_PROXY=http://192.168.10.4:10809
    export HTTPS_PROXY=http://192.168.10.4:10809
    export NO_PROXY=localhost,127.0.0.1,*.local,*.internal
    
    # 配置 Git 代理
    git config --global http.proxy http://192.168.10.4:10809
    git config --global https.proxy http://192.168.10.4:10809
    
    # 配置 npm 代理
    npm config set proxy http://192.168.10.4:10809
    npm config set https-proxy http://192.168.10.4:10809
    
    echo "代理配置完成"
fi

# 安装 Node.js 18（如果不存在）
if ! command -v node &> /dev/null; then
    echo "安装 Node.js 18..."
    curl -fsSL https://nodejs.org/dist/v18.19.0/node-v18.19.0-linux-x64.tar.xz | tar -xJ -C /usr/local
    ln -sf /usr/local/node-v18.19.0-linux-x64/bin/node /usr/local/bin/node
    ln -sf /usr/local/node-v18.19.0-linux-x64/bin/npm /usr/local/bin/npm
    ln -sf /usr/local/node-v18.19.0-linux-x64/bin/npx /usr/local/bin/npx
fi

# 配置 npm 国内镜像
npm config set registry https://registry.npmmirror.com

# 安装 Vue CLI（如果不存在）
if ! command -v vue &> /dev/null; then
    echo "安装 Vue CLI..."
    npm install -g @vue/cli@5.0.8 @vue/cli-service@5.0.8
fi

# 安装其他必要工具
echo "安装开发工具..."
sudo apt-get update && sudo apt-get install -y \
    mysql-client \
    redis-tools \
    net-tools \
    iputils-ping \
    telnet \
    htop \
    tree \
    unzip \
    zip \
    jq

# 配置 Maven 国内镜像
echo "配置 Maven 国内镜像..."
sudo mkdir -p /usr/share/maven/conf
sudo cat > /usr/share/maven/conf/settings.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

# 配置 pip 国内镜像
pip3 config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
pip3 config set global.trusted-host pypi.tuna.tsinghua.edu.cn

echo "============================================"
echo "  环境配置完成！"
echo "============================================"
echo "已安装的工具:"
echo "  Java: $(java -version 2>&1 | head -n 1)"
echo "  Maven: $(mvn -version | head -n 1)"
echo "  Node.js: $(node --version 2>/dev/null || echo '未安装')"
echo "  npm: $(npm --version 2>/dev/null || echo '未安装')"
echo "  Python: $(python3 --version)"
echo ""
echo "项目目录: /workspace"
echo "切换到 developer 用户: su - developer"
echo "============================================"