import 'package:flutter/material.dart';

import 'login_controller.dart';

final class LoginScreen extends StatefulWidget {
  const LoginScreen({
    required this.controller,
    this.errorMessage,
    super.key,
  });

  final LoginController controller;
  final String? errorMessage;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

final class _LoginScreenState extends State<LoginScreen> {
  late final TextEditingController _hostController;
  late final TextEditingController _portController;
  late final TextEditingController _userIdController;
  late final TextEditingController _platformController;
  late final TextEditingController _tokenController;

  @override
  void initState() {
    super.initState();
    _hostController = TextEditingController(text: '127.0.0.1');
    _portController = TextEditingController(text: '5148');
    _userIdController = TextEditingController();
    _platformController = TextEditingController(text: '2');
    _tokenController = TextEditingController();
  }

  @override
  void dispose() {
    _hostController.dispose();
    _portController.dispose();
    _userIdController.dispose();
    _platformController.dispose();
    _tokenController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: AnimatedBuilder(
              animation: widget.controller,
              builder: (context, _) {
                final errorMessage =
                    widget.errorMessage ?? widget.controller.errorMessage;
                return Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    Text('CheeseIM Login',
                        style: Theme.of(context).textTheme.headlineSmall),
                    if (errorMessage != null) ...<Widget>[
                      const SizedBox(height: 12),
                      Text(
                        errorMessage,
                        style: const TextStyle(color: Colors.redAccent),
                      ),
                    ],
                    const SizedBox(height: 16),
                    TextField(
                      controller: _hostController,
                      decoration: const InputDecoration(labelText: 'Host'),
                    ),
                    TextField(
                      controller: _portController,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Port'),
                    ),
                    TextField(
                      controller: _userIdController,
                      decoration: const InputDecoration(labelText: 'User ID'),
                    ),
                    TextField(
                      controller: _platformController,
                      keyboardType: TextInputType.number,
                      decoration:
                          const InputDecoration(labelText: 'Platform ID'),
                    ),
                    TextField(
                      controller: _tokenController,
                      decoration: const InputDecoration(labelText: 'Token'),
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: widget.controller.submitting ? null : _submit,
                      child: Text(widget.controller.submitting
                          ? 'Connecting...'
                          : 'Connect'),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _submit() {
    return widget.controller.login(
      host: _hostController.text.trim(),
      port: int.tryParse(_portController.text.trim()) ?? 0,
      userId: _userIdController.text.trim(),
      token: _tokenController.text.trim(),
      platformId: int.tryParse(_platformController.text.trim()) ?? 0,
    );
  }
}
