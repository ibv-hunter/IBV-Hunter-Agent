// Modify from testing-platform/adapters/grpcAdapter.ts in cline source code.
import { cline, host } from './support';
import { credentials, ClientReadableStream } from "@grpc/grpc-js"
import { promisify } from "util"

// Based on cline v3.28.4
const serviceRegistry = {
	"cline.AccountService": cline.AccountServiceClient,
	"cline.BrowserService": cline.BrowserServiceClient,
	"cline.CheckpointsService": cline.CheckpointsServiceClient,
	"cline.CommandsService": cline.CommandsServiceClient,
	"cline.FileService": cline.FileServiceClient,
	"cline.McpService": cline.McpServiceClient,
	"cline.ModelsService": cline.ModelsServiceClient,
	"cline.SlashService": cline.SlashServiceClient,
	"cline.StateService": cline.StateServiceClient,
	"cline.TaskService": cline.TaskServiceClient,
	"cline.UiService": cline.UiServiceClient,
	"cline.WebService": cline.WebServiceClient,
} as const

export type ServiceClients = {
	-readonly [K in keyof typeof serviceRegistry]: InstanceType<(typeof serviceRegistry)[K]>
}

export class GrpcAdapter {
	private clients: Partial<ServiceClients> = {}

	constructor(address: string) {
		for (const [name, Client] of Object.entries(serviceRegistry)) {
			this.clients[name as keyof ServiceClients] = new (Client as any)(address, credentials.createInsecure())
		}
	}

	/**
	* Single call to the cline gRPC API
	* This optimizes single gRPC calls into Promises, which is more consistent with modern JavaScript syntax. 
	*
	* Note: This method cannot be used for stream-type calls; it will wait for all data to be returned together in the Promise, potentially causing a deadlock.
	*
	* @param service The cline gRPC service name (refer to serviceRegistry)
	* @param method The gRPC method name
	* @param request The request content
	* @returns A Promise object containing the original return value
	*/
	async call(service: keyof ServiceClients, method: string, request: any): Promise<any> {
		const client = this.clients[service]
		if (!client) {
			throw new Error(`No gRPC client registered for service: ${String(service)}`)
		}

		const fn = (client as any)[method]
		if (typeof fn !== "function") {
			throw new Error(`Method ${method} not found on service ${String(service)}`)
		}

		try {
			const fnAsync = promisify(fn).bind(client)
			const response = await fnAsync(request.message)
			return response?.toObject ? response.toObject() : response
		} catch (error) {
			console.error(`[GrpcAdapter] ${service}.${method} failed:`, error)
			throw error
		}
	}

	/**
	* Stream-based invocation of the client gRPC API
	* This only implements Server Streaming, meaning the server returns data using a stream. 
	*
	* @param service Client gRPC service name (see serviceRegistry)
	* @param method gRPC method name
	* @param request Request content
	* @returns Always a ClientReadableStream
	*/
	stream_call(service: keyof ServiceClients, method: string, request: any): ClientReadableStream<any> {
		const client = this.clients[service]
		if (!client) {
			throw new Error(`No gRPC client registered for service: ${String(service)}`)
		}

		const fn = (client as any)[method]
		if (typeof fn !== "function") {
			throw new Error(`Method ${method} not found on service ${String(service)}`)
		}

		try {
			const fnBound = fn.bind(client)
			const response = fnBound(request.message)
			return response
		} catch (error) {
			console.error(`[GrpcAdapter] ${service}.${method} failed:`, error)
			throw error
		}
	}

	close(): void {
		for (const client of Object.values(this.clients)) {
			if (client && typeof (client as any).close === "function") {
				; (client as any).close()
			}
		}
	}
}
