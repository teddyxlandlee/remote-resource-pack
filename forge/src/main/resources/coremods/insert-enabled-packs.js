var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI')
var Opcodes = Java.type('org.objectweb.asm.Opcodes')
var InsnList = Java.type('org.objectweb.asm.tree.InsnList')
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode')

function initializeCoreMod() {
	return {
		'insert_enabled_packs': {
			'target': {
				'type': 'METHOD',
				'class': 'net.minecraft.client.Minecraft',
				'methodName': '<init>',
				'methodDesc': '(Lnet/minecraft/client/main/GameConfig;)V'
			},
			'transformer': function(method) {
				var before = new InsnList()
				before.add(new InsnNode(Opcodes.DUP))	// dup a PackRepository

				var after = new InsnList()
				after.add(ASMAPI.buildMethodCall(	// already have a PackRepository in stack
					'xland/mcmod/remoteresourcepack/RemoteResourcePack',
					'insertEnabledPacks',
					'(Lnet/minecraft/server/packs/repository/PackRepository;)V',
					ASMAPI.MethodType.STATIC
				))

				ASMAPI.insertInsnList(	// before
					method, ASMAPI.MethodType.VIRTUAL,
					'net/minecraft/server/packs/repository/PackRepository',
					ASMAPI.mapMethod('m_10506_'),
					'()V',
					before, ASMAPI.InsertMode.INSERT_BEFORE
				)
				ASMAPI.insertInsnList(	// after
					method, ASMAPI.MethodType.VIRTUAL,
					'net/minecraft/server/packs/repository/PackRepository',
					ASMAPI.mapMethod('m_10506_'),
					'()V',
					after, ASMAPI.InsertMode.INSERT_AFTER
				)
				return method
			}
		}
	}
}
