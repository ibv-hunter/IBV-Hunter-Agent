import * as grpc from "@grpc/grpc-js"
import { host } from "../support";

export class TestingServiceImpl implements host.TestingServiceServer {
  [name: string]: grpc.UntypedHandleCall;

  getWebviewHtml: grpc.handleUnaryCall<host.GetWebviewHtmlRequest, host.GetWebviewHtmlResponse> = (call, callback) => {
    const response = host.GetWebviewHtmlResponse.create({
      html: "<html><body>We do not support WebView in standalone mode.</body></html>",
    });
    callback(null, response);
  };
}