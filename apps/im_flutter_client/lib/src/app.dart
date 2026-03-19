import 'dart:async';

import 'package:flutter/material.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

import 'core/bootstrap.dart';
import 'features/auth/login_controller.dart';
import 'features/auth/login_screen.dart';
import 'features/chat/chat_controller.dart';
import 'features/chat/chat_shell.dart';
import 'features/chat/connection_banner.dart';

final class ImFlutterApp extends StatefulWidget {
  const ImFlutterApp({required this.dependencies, super.key});

  final AppDependencies dependencies;

  @override
  State<ImFlutterApp> createState() => _ImFlutterAppState();
}

final class _ImFlutterAppState extends State<ImFlutterApp> {
  late final LoginController _loginController;
  late final ChatController _chatController;
  late ConnectionSnapshot _snapshot;
  StreamSubscription<ConnectionSnapshot>? _snapshotSubscription;
  String? _loginErrorMessage;
  bool _hasReadySession = false;
  bool _loginRequired = true;

  bool get _showsLogin => _loginRequired;

  @override
  void initState() {
    super.initState();
    _loginController = LoginController(widget.dependencies.client);
    _chatController = ChatController(widget.dependencies.client);
    _snapshot = widget.dependencies.client.snapshot;
    _hasReadySession = _snapshot.state == ConnectionLifecycle.ready;
    _loginRequired = _computeLoginRequired(_snapshot);
    if (_requiresLoginError(_snapshot.errorKind)) {
      _loginErrorMessage = _snapshot.lastError;
    }
    _snapshotSubscription =
        widget.dependencies.client.connectionSnapshots.listen((snapshot) {
      if (!mounted) {
        return;
      }
      setState(() {
        _snapshot = snapshot;
        if (snapshot.state == ConnectionLifecycle.ready) {
          _hasReadySession = true;
        }
        _loginRequired = _computeLoginRequired(snapshot);
        if (_requiresLoginError(snapshot.errorKind)) {
          _loginErrorMessage = snapshot.lastError;
        } else if (snapshot.state == ConnectionLifecycle.ready) {
          _loginErrorMessage = null;
        }
      });
    });
  }

  @override
  void dispose() {
    _snapshotSubscription?.cancel();
    _loginController.dispose();
    _chatController.dispose();
    unawaited(widget.dependencies.client.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: _showsLogin
          ? LoginScreen(
              controller: _loginController,
              errorMessage: _loginErrorMessage,
            )
          : Scaffold(
              appBar: AppBar(title: const Text('Conversations')),
              body: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  ConnectionBanner(snapshot: _snapshot),
                  Expanded(
                    child: ChatShell(controller: _chatController),
                  ),
                ],
              ),
            ),
    );
  }

  bool _computeLoginRequired(ConnectionSnapshot snapshot) {
    if (_requiresLoginError(snapshot.errorKind)) {
      return true;
    }
    if (_hasReadySession) {
      return false;
    }
    return switch (snapshot.state) {
      ConnectionLifecycle.idle => true,
      ConnectionLifecycle.ready => false,
      _ => true,
    };
  }

  bool _requiresLoginError(ClientErrorKind? errorKind) {
    return errorKind == ClientErrorKind.authRejected ||
        errorKind == ClientErrorKind.forceLogout;
  }
}
