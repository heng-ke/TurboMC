/*
 * Scratch 全局对象：扩展脚本以 IIFE 形式接收它（(function(Scratch){...})(Scratch)）。
 * 只提供扩展实际用到的常量与注册入口，不含 VM 依赖。
 */

var Scratch = {
	BlockType: {HAT: 'hat', COMMAND: 'command', REPORTER: 'reporter', BOOLEAN: 'boolean', BUTTON: 'button', LABEL: 'label'},
	ArgumentType: {
		STRING: 'string', NUMBER: 'number', BOOLEAN: 'boolean',
		ANGLE: 'angle', COLOR: 'color', NOTE: 'note'
	},
	TargetType: {SPRITE: 'sprite', STAGE: 'stage'},
	extensions: {
		register: function (instance) { Scratch.__instance = instance; }
	}
};

var navigator = {language: 'zh-CN'};
