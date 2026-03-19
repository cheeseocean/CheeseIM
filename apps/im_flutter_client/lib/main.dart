import 'package:flutter/material.dart';

import 'src/app.dart';
import 'src/core/bootstrap.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final dependencies = await bootstrap();
  runApp(ImFlutterApp(dependencies: dependencies));
}
