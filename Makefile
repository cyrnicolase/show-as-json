.PHONY: help build clean install test run dist

# 项目配置
PLUGIN_NAME := show-as-json-plugin
VERSION := 1.0.12
BUILD_DIR := build
DIST_DIR := $(BUILD_DIR)/distributions
PLUGIN_ZIP := $(DIST_DIR)/$(PLUGIN_NAME)-$(VERSION).zip

# Gradle 命令
GRADLE := ./gradlew
GRADLE_FLAGS := --no-daemon

# 默认目标
.DEFAULT_GOAL := help

## help: 显示帮助信息
help:
	@echo "可用的命令:"
	@echo "  make build      - 构建插件 (生成 ZIP 文件)"
	@echo "  make clean      - 清理构建文件"
	@echo "  make dist       - 构建并显示插件文件位置"
	@echo "  make install    - 安装插件到 DataGrip (需要设置 DATAGRIP_PLUGINS_DIR)"
	@echo "  make run        - 运行插件进行测试 (启动沙盒 DataGrip 实例)"
	@echo "  make test       - 运行测试"
	@echo "  make clean-build - 清理后重新构建"
	@echo ""
	@echo "环境变量:"
	@echo "  DATAGRIP_PLUGINS_DIR - DataGrip 插件目录路径"
	@echo "    示例 (macOS): /Users/$(USER)/Library/Application Support/JetBrains/DataGrip2025.3/plugins"
	@echo "    示例 (Windows): %%APPDATA%%\\JetBrains\\DataGrip2025.3\\plugins"
	@echo "    示例 (Linux): ~/.local/share/JetBrains/DataGrip2025.3/plugins"

## build: 构建插件
build:
	@echo "正在构建插件..."
	$(GRADLE) buildPlugin $(GRADLE_FLAGS)
	@echo ""
	@echo "构建完成！插件文件位于: $(PLUGIN_ZIP)"
	@ls -lh $(PLUGIN_ZIP) 2>/dev/null || echo "文件未找到"

## clean: 清理构建文件
clean:
	@echo "正在清理构建文件..."
	$(GRADLE) clean $(GRADLE_FLAGS)
	@echo "清理完成！"

## clean-build: 清理后重新构建
clean-build: clean build

## dist: 构建并显示插件文件信息
dist: build
	@echo ""
	@echo "=========================================="
	@echo "插件构建信息:"
	@echo "=========================================="
	@echo "插件名称: $(PLUGIN_NAME)"
	@echo "版本: $(VERSION)"
	@echo "文件位置: $(PLUGIN_ZIP)"
	@if [ -f $(PLUGIN_ZIP) ]; then \
		echo "文件大小: $$(ls -lh $(PLUGIN_ZIP) | awk '{print $$5}')"; \
		echo ""; \
		echo "安装方法:"; \
		echo "1. 打开 DataGrip"; \
		echo "2. Settings/Preferences → Plugins"; \
		echo "3. 点击齿轮图标 → Install Plugin from Disk..."; \
		echo "4. 选择文件: $(PLUGIN_ZIP)"; \
		echo "5. 重启 DataGrip"; \
	else \
		echo "错误: 插件文件未找到！"; \
	fi
	@echo "=========================================="

## install: 安装插件到 DataGrip
install: build
	@if [ -z "$(DATAGRIP_PLUGINS_DIR)" ]; then \
		echo "错误: 请设置 DATAGRIP_PLUGINS_DIR 环境变量"; \
		echo ""; \
		echo "示例 (macOS):"; \
		echo "  export DATAGRIP_PLUGINS_DIR=~/Library/Application\\ Support/JetBrains/DataGrip2025.3/plugins"; \
		echo "  make install"; \
		echo ""; \
		echo "示例 (Linux):"; \
		echo "  export DATAGRIP_PLUGINS_DIR=~/.local/share/JetBrains/DataGrip2025.3/plugins"; \
		echo "  make install"; \
		exit 1; \
	fi
	@echo "正在安装插件到: $(DATAGRIP_PLUGINS_DIR)"
	@mkdir -p "$(DATAGRIP_PLUGINS_DIR)"
	@if [ -f $(PLUGIN_ZIP) ]; then \
		cp $(PLUGIN_ZIP) "$(DATAGRIP_PLUGINS_DIR)/"; \
		echo "插件已复制到: $(DATAGRIP_PLUGINS_DIR)/$(PLUGIN_NAME)-$(VERSION).zip"; \
		echo ""; \
		echo "请重启 DataGrip 以加载插件"; \
	else \
		echo "错误: 插件文件未找到: $(PLUGIN_ZIP)"; \
		echo "请先运行 'make build' 构建插件"; \
		exit 1; \
	fi

## run: 运行插件进行测试
run:
	@echo "正在启动沙盒 DataGrip 实例进行测试..."
	$(GRADLE) runIde $(GRADLE_FLAGS)

## test: 运行测试
test:
	@echo "正在运行测试..."
	$(GRADLE) test $(GRADLE_FLAGS)

## check: 检查代码和配置
check:
	@echo "正在检查代码..."
	$(GRADLE) check $(GRADLE_FLAGS)

## version: 显示版本信息
version:
	@echo "插件名称: $(PLUGIN_NAME)"
	@echo "版本: $(VERSION)"
	@echo "构建目录: $(BUILD_DIR)"

